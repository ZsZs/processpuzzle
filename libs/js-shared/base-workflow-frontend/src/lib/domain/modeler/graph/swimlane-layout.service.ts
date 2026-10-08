import dagre from '@dagrejs/dagre';
import { inject, Injectable } from '@angular/core';
import { FLOW_KINDS, isBoundaryNode, isLaneNode, WorkflowEdge, WorkflowLaneNode, WorkflowNode } from '../workflow-graph';
import { placeBoundaryEvents, rankingEdges } from './boundary-event-placement';
import { WorkflowLayoutService } from './workflow-layout.service';

/**
 * The one node box every element is placed as, and the one the columns are pitched by.
 *
 * **Stated on the node, not merely laid out against.** `WorkflowElementNodeComponent` fixes its width at
 * 170px in CSS but is `autoSize: true`, so its *height* is whatever its description renders to — and a card
 * taller than the row it was spaced for would spill past the bottom of its own lane, with nothing to correct
 * it. So this layout writes `size` and `autoSize: false` onto every node it places, which makes the arithmetic
 * here and the rendered box the same number by construction.
 *
 * `autoSize: false` is not decoration: ng-diagram's `NodeSizeDirective` defaults `autoSize` to **true** and,
 * when it is true, *discards* an explicit `size` and re-applies its own default. A lane emitted without it
 * keeps none of the width and height computed below.
 */
const NODE_SIZE = { width: 170, height: 76 };

/** The lane's left column, holding its symbol and the role's name. No task is ever placed in it. */
const HEADER_WIDTH = 140;

/** Gaps. `COLUMN_GAP` is the generous one — the columns are what the flow's edges cross. */
const COLUMN_GAP = 70;
const ROW_GAP = 16;
const LANE_GAP = 8;
const LANE_PADDING = 16;
const STRIP_GAP = 48;

/**
 * Places one workflow perspective as BPMN-style swimlanes: a band per performing role, the flow running left
 * to right across all of them, and everything that is not part of the flow — a task or a
 * start or end event — in a strip underneath.
 *
 * ## Why it is not the flow layout with extra steps
 *
 * {@link WorkflowLayoutService} asks Dagre for positions and uses them. This service asks Dagre for one
 * thing only — **which column** each task belongs in — and computes every coordinate itself. The vertical
 * axis is not Dagre's to decide here: it *is* the lane, so the y of a task is a fact about who performs it,
 * not about how a graph packs. Reading Dagre's `y` would scatter one role's tasks across three bands.
 *
 * Dagre is still worth asking, rather than walking the dependencies by hand, because its ranking tightens
 * slack: given `A → D` alongside `A → B → C → D`, longest-path ranking would leave A three columns from D
 * and draw one very long edge, where Dagre's network simplex pulls A to one column from D. That is the whole
 * of what it is used for — `graph.node(id).rank`, never `x` or `y`.
 *
 * ## What it does not attempt
 *
 * No obstacle avoidance. ng-diagram's orthogonal router draws an edge that skips a column straight through
 * the column between, and nothing here moves a node out of its way — Dagre's crossing minimisation is
 * exactly what discarding `y` gives up. Acceptable because a lane's tasks are *ordered* by the flow, so a
 * skipping edge is the exception; and because the alternative is to let a role's tasks leave their lane.
 *
 * No knowledge of the persisted arrangement, which the Workflows perspective does have. This service places
 * *everything*, every time — it has to, since a lane's band is measured from the tasks in it — and
 * `applySavedLayout` then moves whatever the user arranged on top of that result. Keeping the two apart is
 * what makes a task added since the last save appear in the right lane rather than at the origin: it is
 * placed here and simply not overridden. See `workflow-diagram.converter.ts`.
 */
@Injectable({ providedIn: 'root' })
export class SwimlaneLayoutService {
  private readonly flowLayout = inject(WorkflowLayoutService);

  /**
   * Returns the nodes with positions, sizes and lane boxes filled in. The input array is never mutated —
   * ng-diagram's model owns these objects and its reactivity runs through the adapter rather than through
   * in-place writes.
   *
   * A graph with no lanes is handed to {@link WorkflowLayoutService} whole. That is the Lanes toggle turned
   * off, and a flat left-to-right flow is what the plain layout already produces — reimplementing it here
   * would be a second answer to a question already answered.
   */
  place(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowNode[] {
    const lanes = nodes.filter(isLaneNode);
    if (nodes.length === 0 || lanes.length === 0) return this.flowLayout.place(nodes, edges);

    // The flow is the tasks *and* the start and end events: the events ride the same `sequence` edges, so
    // they take a column of their own — a start event the one before its roots, the end the one after the
    // last task — and a band's row in it, rather than a place in the strip.
    // A boundary event is neither: it takes no column and no row, but is pinned to its task once the task is
    // placed. Its outgoing edges are ranked as its task's, so what follows it still lands a column on.
    const flowNodes = nodes.filter((node) => !isLaneNode(node) && !isBoundaryNode(node) && FLOW_KINDS.includes(node.data.kind));
    const loose = nodes.filter((node) => !isLaneNode(node) && !FLOW_KINDS.includes(node.data.kind));
    const boundaries = nodes.filter(isBoundaryNode);
    const ranked = rankingEdges(nodes, edges);

    const columnOf = this.columns(flowNodes, ranked);
    const flow = flowNodes.map((node) => (node.data.kind === 'end' ? rehomeEnd(node, edges, columnOf, flowNodes) : node));
    const layout = measureBands(lanes, flow, columnOf);
    const laneWidth = HEADER_WIDTH + LANE_PADDING + widestColumn(columnOf) * (NODE_SIZE.width + COLUMN_GAP) + NODE_SIZE.width + LANE_PADDING;

    const placedLanes = lanes.map((lane) => placeLane(lane, layout.bands.get(lane.id) as Band, laneWidth));
    const placedTasks = flow.map((node) => placeTask(node, layout, columnOf));
    const placedLoose = placeStrip(loose, ranked, columnOf, stripTop(layout.bands));

    // In the input's order rather than lane-first: the converter already ordered the lanes ahead of their
    // children, and reordering here would silently override whatever it decided.
    const byId = new Map([...placedLanes, ...placedTasks, ...placedLoose, ...boundaries].map((node) => [node.id, node]));
    return placeBoundaryEvents(nodes.map((node) => byId.get(node.id) ?? node));
  }

  /**
   * Which column each node of the flow — task or event — sits in — its Dagre rank, renumbered to consecutive integers.
   *
   * The renumbering is not cosmetic: Dagre reports ranks two apart (0, 2, 4) because it inserts a rank
   * between every pair for edge labels, so using them as column indices would leave every other column
   * empty and double the diagram's width.
   *
   * Only the flow edges are given to Dagre. A data or tool edge would rank the artifact it points at as a
   * column of its own and push the tasks after it sideways, which is the one thing the columns must not
   * depend on.
   */
  private columns(flow: WorkflowNode[], edges: WorkflowEdge[]): Map<string, number> {
    if (flow.length === 0) return new Map();

    const graph = new dagre.graphlib.Graph();
    graph.setGraph({ rankdir: 'LR', nodesep: ROW_GAP, ranksep: COLUMN_GAP });
    graph.setDefaultEdgeLabel(() => ({}));
    flow.forEach((node) => graph.setNode(node.id, { ...NODE_SIZE }));

    const flowIds = new Set(flow.map((node) => node.id));
    edges
      .filter((edge) => edge.data?.relation === 'sequence' || edge.data?.relation === 'implicit')
      // Both ends have to be nodes of this flow, and the two ends have to differ: Dagre invents a node for
      // an id it has not been given, and a self-edge would make it lay out a phantom loop.
      .filter((edge) => flowIds.has(edge.source) && flowIds.has(edge.target) && edge.source !== edge.target)
      .forEach((edge) => graph.setEdge(edge.source, edge.target));

    dagre.layout(graph);

    // Cast because `@dagrejs/dagre` types `node()` as its input label and does not declare the `rank` the
    // ranking phase writes onto it. The fallback covers a node with no flow edge at all — Dagre does rank an
    // isolated node, but a layout is not the place to discover that a future version stopped.
    const ranks = flow.map((node) => (graph.node(node.id) as { rank?: number } | undefined)?.rank ?? 0);
    const columnByRank = new Map([...new Set(ranks)].sort((left, right) => left - right).map((rank, column) => [rank, column]));
    return new Map(flow.map((node, index) => [node.id, columnByRank.get(ranks[index]) as number]));
  }
}

// region private helper functions
/** One lane's vertical extent, and how many rows deep it has to be to hold its busiest column. */
interface Band {
  top: number;
  height: number;
  rows: number;
}

/** Everything the measuring pass works out: where each band is, and which row each task takes in it. */
interface Layout {
  bands: Map<string, Band>;
  rowByTaskId: Map<string, number>;
}

/** The rightmost column any task occupies. */
function widestColumn(columnOf: Map<string, number>): number {
  return Math.max(0, ...columnOf.values());
}

/** Where a column's left edge is, measured from the lane's own left edge. */
function columnX(column: number): number {
  return HEADER_WIDTH + LANE_PADDING + column * (NODE_SIZE.width + COLUMN_GAP);
}

/**
 * Every lane's height and top, plus the row each task takes within its own band.
 *
 * Two passes, because they cannot be done in one: a lane's top is the sum of the heights of the lanes above
 * it, so no lane's position is known until every height is.
 *
 * A lane's depth is its **busiest column**, not its task count — three tasks in three columns is one row
 * deep. And it is at least one row, so a lane whose tasks were all hidden by a toggle is still a band rather
 * than a line.
 *
 * A task's row is its position among the tasks sharing its cell, in the order the nodes arrived, which is
 * the workflow's declaration order. Determinism is the point: the graph is rebuilt on every catalog change
 * and every toggle, and a row order that came out differently each time would make the nodes jump.
 */
function measureBands(lanes: WorkflowLaneNode[], tasks: WorkflowNode[], columnOf: Map<string, number>): Layout {
  const rowsByLane = new Map(lanes.map((lane) => [lane.id, 1]));
  const rowByTaskId = new Map<string, number>();
  const depthByCell = new Map<string, number>();

  tasks.forEach((task) => {
    if (task.groupId === undefined) return;
    const cell = `${task.groupId}@${columnOf.get(task.id)}`;
    const row = depthByCell.get(cell) ?? 0;
    depthByCell.set(cell, row + 1);
    rowByTaskId.set(task.id, row);
    rowsByLane.set(task.groupId, Math.max(rowsByLane.get(task.groupId) ?? 1, row + 1));
  });

  const bands = new Map<string, Band>();
  let top = 0;
  lanes.forEach((lane) => {
    const rows = rowsByLane.get(lane.id) as number;
    const height = 2 * LANE_PADDING + rows * NODE_SIZE.height + (rows - 1) * ROW_GAP;
    bands.set(lane.id, { top, height, rows });
    top += height + LANE_GAP;
  });

  return { bands, rowByTaskId };
}

/** Where the strip of artifacts and tools starts — under the last lane. */
function stripTop(bands: Map<string, Band>): number {
  return Math.max(0, ...[...bands.values()].map((band) => band.top + band.height)) + STRIP_GAP;
}

/**
 * One lane's box. Every lane shares one x and one width, which is what BPMN pools do and what makes a column
 * mean the same thing in every band.
 *
 * The size is stated and `autoSize` turned off — see {@link NODE_SIZE}. Read-only comes from the canvas,
 * which locks every node it is given, so nothing here has to say it.
 */
function placeLane(lane: WorkflowLaneNode, band: Band, width: number): WorkflowLaneNode {
  return { ...lane, position: { x: 0, y: band.top }, size: { width, height: band.height }, autoSize: false };
}

/**
 * One task's box: its column decides the x, its lane the y, and its row within that band comes from the
 * measuring pass.
 *
 * A task with no lane cannot arise — the converter gives every task a `groupId` whenever it emits lanes at
 * all — but it is placed in the first band rather than at the origin if one ever does, because a node at
 * `0,0` looks like a bug in the diagram rather than a gap in the model.
 *
 * A start or end event is placed the same way but keeps the smaller box the converter gave it, centred in
 * its cell, so that the circle sits on the line its neighbours' edges run along.
 */
function placeTask(task: WorkflowNode, layout: Layout, columnOf: Map<string, number>): WorkflowNode {
  const band = (task.groupId === undefined ? undefined : layout.bands.get(task.groupId)) ?? { top: 0, height: 0, rows: 1 };
  const row = layout.rowByTaskId.get(task.id) ?? 0;
  const size = task.data.kind === 'task' ? NODE_SIZE : (task.size ?? NODE_SIZE);
  const inset = { x: (NODE_SIZE.width - size.width) / 2, y: (NODE_SIZE.height - size.height) / 2 };
  return {
    ...task,
    position: { x: columnX(columnOf.get(task.id) ?? 0) + inset.x, y: band.top + LANE_PADDING + row * (NODE_SIZE.height + ROW_GAP) + inset.y },
    size: { width: size.width, height: size.height },
    autoSize: false,
  };
}

/**
 * The end event, moved into the lane of the feeder that lands in the last column — where the flow actually
 * finishes, which the converter could not know before the columns were ranked. Of several feeders in that
 * column the last declared wins, the same tie-break the converter's first guess used.
 *
 * Left where the converter put it when no feeder has a lane, which is the Lanes toggle being off.
 */
function rehomeEnd(end: WorkflowNode, edges: WorkflowEdge[], columnOf: Map<string, number>, flow: WorkflowNode[]): WorkflowNode {
  const flowById = new Map(flow.map((node) => [node.id, node]));
  let last: WorkflowNode | undefined;
  edges
    .filter((edge) => edge.target === end.id && edge.data?.relation === 'sequence')
    .map((edge) => flowById.get(edge.source))
    .forEach((feeder) => {
      if (feeder?.groupId === undefined) return;
      if (last === undefined || (columnOf.get(feeder.id) ?? 0) >= (columnOf.get(last.id) ?? 0)) last = feeder;
    });
  return last === undefined ? end : { ...end, groupId: last.groupId };
}

/**
 * The strip of artifacts and tools, under the lanes rather than in them.
 *
 * Under, because a node that is not a member of a lane but overlaps its box reads as if it were one — and
 * ng-diagram would happily draw it there, since a lane contains only the nodes whose `groupId` names it.
 *
 * Each is placed in the column of the task it is attached to, so its line to that task is short and vertical,
 * and stacked when several share a column. An object written by a task hangs under its producer and lets
 * the task reading it on reach back; anything else under the earliest flow node it is joined to — see
 * {@link anchorColumn}.
 */
function placeStrip(loose: WorkflowNode[], edges: WorkflowEdge[], columnOf: Map<string, number>, top: number): WorkflowNode[] {
  const depthByColumn = new Map<number, number>();
  return loose.map((node) => {
    const column = anchorColumn(node.id, edges, columnOf);
    const row = depthByColumn.get(column) ?? 0;
    depthByColumn.set(column, row + 1);
    return { ...node, position: { x: columnX(column), y: top + row * (NODE_SIZE.height + ROW_GAP) }, size: { ...NODE_SIZE }, autoSize: false };
  });
}

/**
 * The column a strip node hangs under: its earliest *producer's* when some task writes it, otherwise the
 * earliest flow node it is joined to at all, and the first column when it is joined to none.
 *
 * The producer first, because an artifact is drawn once per state and the task leaving it in that state is
 * what the node is about — `[CONFIRMED]` belongs under the task that confirms the order, not under one that
 * merely reads it, even should that reader come earlier in a flow that loops back.
 */
function anchorColumn(nodeId: string, edges: WorkflowEdge[], columnOf: Map<string, number>): number {
  const columnsOf = (joined: WorkflowEdge[]) =>
    joined.map((edge) => columnOf.get(edge.source === nodeId ? edge.target : edge.source)).filter((column): column is number => column !== undefined);
  const joined = edges.filter((edge) => edge.source === nodeId || edge.target === nodeId);
  const producers = columnsOf(joined.filter((edge) => edge.target === nodeId && edge.data?.relation === 'output'));
  const columns = producers.length > 0 ? producers : columnsOf(joined);
  return columns.length === 0 ? 0 : Math.min(...columns);
}
// endregion
