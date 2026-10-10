import { ArtifactDefinition } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { StepDefinition, TaskDefinition, TaskStepType } from '../../definition/task-definition';
import { ToolDefinition } from '../../definition/tool-definition';
import { EventDirection, EventUse, JoinType, StartEvent, TaskArtifactState, timerOf, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from '../../definition/workflow';
import { EntityReference, toReferenceIds } from '../../reference-ids';
import {
  elementEdgeId,
  elementNodeId,
  BOUNDARY_EVENT_NODE_SIZE,
  EVENT_NODE_SIZE,
  laneNodeId,
  WORKFLOW_LANE_TYPE,
  WORKFLOW_NODE_TYPE,
  WORKFLOW_RELATION_EDGE_TYPE,
  WorkflowEdge,
  WorkflowGraph,
  WorkflowNode,
  WorkflowRelation,
} from '../workflow-graph';

/**
 * Which layers of the workflow are on screen, and the three strings the screen has to lend the converter.
 *
 * Every flag defaults to `true`: the whole workflow is the useful first sight of it, and a toggle exists to
 * take something *away*. They are applied here rather than in the canvas so that a hidden layer's nodes and
 * edges never reach the layout — a filtered layout re-ranks the remaining flow instead of leaving the gaps
 * the hidden nodes occupied.
 *
 * `labels` is here because this module holds no transloco. Three of the diagram's labels are not data — the
 * name of the lane for a task nobody is shown to perform, the word marking an `ANY` join, and the name of the
 * derived end event — so the screen resolves them and passes them down, the same way `RoleResponsibilityGraphConverter` takes the id
 * to highlight from the screen rather than discovering it.
 */
export interface WorkflowFlowGraphOptions {
  /** Group tasks into one lane per performing role. Off draws a flat left-to-right flow. */
  lanes?: boolean;
  /** Artifact nodes, their input/output edges, and the artifacts the start events wait for. */
  data?: boolean;
  /** Tool nodes and the service steps that call them. */
  tools?: boolean;
  labels?: {
    /** Lane name for tasks with no stated performer. */
    unassignedLane?: string;
    /** Written on the incoming edges of a task whose `dependsOn` set is satisfied by any one of them. */
    anyJoin?: string;
    /** Name of the end event, which the model does not have and so cannot name. */
    endEvent?: string;
  };
}

/**
 * The Workflows perspective: one workflow's task flow, as BPMN-style swimlanes.
 *
 * The second of the three perspectives, and — as with the first — the whole of what makes it *the Workflows
 * one*. Everything else in `domain/modeler` and `feature/modeler` is kind-agnostic, and the lane node and
 * the relation edge it introduces are registrations beside the existing element template, not replacements
 * for it.
 *
 * ## What the flow is
 *
 * The contract has **no gateways, no conditions and no ordering index**. A workflow's flow is a dependency
 * DAG stated *backwards*: each `WorkflowTaskAssignment.dependsOn` names the sibling tasks that must finish
 * before it, so every sequence edge here is a `dependsOn` entry read in reverse. Three things qualify it,
 * and all three are drawn:
 *
 * - **The root.** A task depending on nothing is eligible from the start, so every one of the workflow's
 *   `startEvents` is drawn as a BPMN start event feeding all of them — the engine activates every root
 *   whichever event fired. With the data layer on, an event's `requiredArtifacts` are drawn feeding it.
 * - **The end.** The model has no end element: an instance completes when every task has. One end event is
 *   *derived* nevertheless, fed by every task nothing depends on, so that the flow reads as closed. Not
 *   drawn when no task qualifies — a dependency cycle, which the backend refuses — because an end with
 *   nothing leading into it would claim an exit the flow does not have.
 * - **The join.** `joinType: ANY` on a task with two or more dependencies is the model's only gateway. It
 *   has no element of its own, so it is written on the edges it qualifies.
 * - **The intermediate events.** An `EventUse` takes part in the flow like a task — its `dependsOn` and a
 *   task's may name tasks and events alike — so it is a node of the flow, a root or a sink like any task.
 *   It has no performer, so it is drawn in the lane of its first dependency, or of its first dependent.
 * - **The boundary events.** An `EventUse` `attachedTo` a task waits only while that task is active, so it
 *   is drawn on the task's border, in the task's lane, rather than as a step of the flow: no edge runs into
 *   it, it is never a root or a sink, and the layout pins it rather than ranking it. What names it in a
 *   `dependsOn` is fed from it like from any event. A dangling `attachedTo` is drawn as a dangling task.
 * - **The implicit order.** Siblings sharing a `dependsOn` set with `parallel: false` run sequentially *in
 *   declaration order* — an ordering that exists nowhere as data. Drawn, because a diagram that showed them
 *   side by side would say they run together and they do not; drawn *distinctly*, because reordering the
 *   rows changes it and reordering rows does not change a `dependsOn`.
 *
 * ## Three decisions rather than mapping
 *
 * **A dangling reference is drawn, not dropped** — the rule this library's first converter set, and one
 * that matters more here: `dependsOn` is authored through a free TAGS control (it names sibling rows of the
 * very list being edited, so no closed option list could be current), which makes an id resolving to
 * nothing an ordinary state of the model rather than a fault. Such a task is drawn `unresolved`, labelled
 * by the raw id, in the unassigned lane — nothing says who would perform it.
 *
 * **Artifacts and tools come from the task catalog, not the workflow.** `Workflow.artifacts` and
 * `.tools` are flat declarations of what the workflow may touch; what each *task* reads, writes and calls
 * is on the referenced `TaskDefinition`. Only the latter can be drawn as a flow, so a declared artifact no
 * task names does not appear — it is on the workflow's own form, where it was authored.
 *
 * **An artifact is drawn once per state it is in, not once.** As in a UML activity diagram, the same class
 * may appear several times, each node the object *in one state*: a task's input edge comes from the object
 * in the assignment's `inputState` for it, its output edge goes to the object in the `outputState`. One
 * task's output state is the next one's input state, so the two meet in one node and the object's
 * lifecycle reads along the flow — `new_order : Order [DRAFT]` → Review → `[CONFIRMED]` → Approve →
 * `[SHIPPED]` — with every edge staying local. A start event's required state is the same node as the
 * first task's input in that state. Where no state is stated the stateless object is drawn, so a workflow
 * that states none draws each artifact exactly once, as it did before states existed.
 *
 * **`extends` is not drawn.** A parent's roles, artifacts, tools and tasks are not merged client-side, and
 * an `override: true` row only means something against a resolved parent. Drawing the parent's id as a
 * lone node would suggest the diagram accounted for what it inherits, which it has not.
 */
export class WorkflowFlowGraphConverter {
  /**
   * Builds the graph of one workflow against the four catalogs as loaded.
   *
   * An absent workflow, or one with no tasks, converts to an empty graph rather than to a diagram of its
   * roles alone: the tab has not loaded yet in the first case and there is no flow in the second, and the
   * screen says so in words. Its start events alone are not drawn either — a start wired straight to an end
   * would draw a workflow that completes the moment it begins, which is not what an unfinished one does.
   */
  static toGraph(
    workflow: Workflow | undefined,
    tasks: TaskDefinition[],
    roles: RoleDefinition[],
    artifacts: ArtifactDefinition[],
    tools: ToolDefinition[],
    options: WorkflowFlowGraphOptions = {},
  ): WorkflowGraph {
    if (!workflow || workflow.tasks.length === 0) return { nodes: [], edges: [] };

    const { lanes = true, data = true, tools: withTools = true } = options;
    const unassignedLaneLabel = options.labels?.unassignedLane ?? '?';
    const anyJoinLabel = options.labels?.anyJoin;
    const endEventLabel = options.labels?.endEvent ?? 'End';

    // Indexed once rather than searched per reference: every task names a role and artifacts, so a
    // per-reference `find` over four catalogs would be quadratic in lists that grow together.
    const tasksById = new Map(tasks.map((task) => [task.id, task]));
    const rolesById = new Map(roles.map((role) => [role.id, role]));
    const artifactsById = new Map(artifacts.map((artifact) => [artifact.id, artifact]));
    const toolsById = new Map(tools.map((tool) => [tool.id, tool]));
    const objectNamesById = new Map(workflow.artifacts.map((use) => [use.artifactDefinitionId, use.objectName]));
    const objectOf = (artifactId: string, state?: string): WorkflowNode =>
      artifactNode(artifactId, artifactsById.get(artifactId), objectNamesById.get(artifactId), state);

    const assignments = workflow.tasks;
    const assignedTaskIds = new Set(assignments.map((assignment) => assignment.taskDefinitionId));
    const events = (workflow.events ?? []).filter((event) => !!event.id);
    const eventIds = new Set(events.map((event) => event.id));
    // Tasks and events share one id namespace, so a `dependsOn` entry is whichever of the two it names.
    const flowNodeIdOf = (id: string): string => elementNodeId(eventIds.has(id) ? 'event' : 'task', id);

    const builder = new GraphBuilder();

    // region tasks, intermediate events and their lanes
    // Every dependency naming no assignment or event of this workflow. Drawn rather than dropped so that the
    // chain does not simply stop, and placed in the unassigned lane because nothing says who would perform it.
    // A boundary event's host is one more such reference: the event is drawn on a task either way.
    const danglingTaskIds = distinct(
      [...assignments.flatMap(dependenciesOf), ...events.flatMap(eventDependenciesOf), ...events.flatMap((event) => hostOf(event) ?? [])].filter(
        (dependencyId) => !assignedTaskIds.has(dependencyId) && !eventIds.has(dependencyId),
      ),
    );

    // Lane order is the order each role's first task appears in, dangling dependencies last — so the lane
    // the workflow starts in is the top one and the invented lane, if any, is the bottom.
    const laneRoleIds = lanes ? distinct([...assignments.map(laneOf), ...(danglingTaskIds.length > 0 ? [UNASSIGNED_ROLE_ID] : [])]) : [];
    laneRoleIds.forEach((roleId) => builder.addNode(laneNode(roleId, rolesById.get(roleId), unassignedLaneLabel)));

    assignments.forEach((assignment) =>
      builder.addNode(taskNode(assignment.taskDefinitionId, tasksById.get(assignment.taskDefinitionId), lanes ? laneOf(assignment) : undefined)),
    );
    danglingTaskIds.forEach((taskId) => builder.addNode(taskNode(taskId, undefined, lanes ? UNASSIGNED_ROLE_ID : undefined)));

    const eventLaneOf = eventLanes(assignments, events);
    events.forEach((event) => builder.addNode(intermediateEventNode(event, lanes ? eventLaneOf(event) : undefined)));
    // endregion

    // region sequence — the flow the model states
    // The join is a property of the whole set, so it is written on each edge of it rather than once.
    // An edge out of a boundary event leaves it downwards: the circle sits on its task's lower edge, and its
    // right-hand port would start the line inside the card.
    const boundaryIds = new Set(events.filter((event) => hostOf(event) !== undefined).map((event) => event.id));
    const addSequence = (targetNodeId: string, dependencies: string[], joinType: JoinType | undefined) => {
      const label = joinType === JoinType.ANY && dependencies.length > 1 ? anyJoinLabel : undefined;
      dependencies.forEach((dependencyId) =>
        builder.addEdge(flowNodeIdOf(dependencyId), targetNodeId, 'sequence', label, boundaryIds.has(dependencyId) ? 'port-bottom' : undefined),
      );
    };
    assignments.forEach((assignment) => addSequence(elementNodeId('task', assignment.taskDefinitionId), dependenciesOf(assignment), assignment.joinType));
    events.forEach((event) => addSequence(elementNodeId('event', event.id), eventDependenciesOf(event), event.joinType));
    // endregion

    // region events — the start events the model states, and the end it implies
    // Emitted after the tasks so that the lanes still precede everything placed in them. An event sits in
    // the lane of the node it is drawn beside: a start event beside the first root, the end beside the last
    // sink — the swimlane layout moves the end to whichever feeder lands in the last column. Roots and sinks
    // are tasks and intermediate events alike, tasks first, each in declaration order.
    const flowItems: FlowItem[] = [
      ...assignments.map((assignment) => ({
        id: assignment.taskDefinitionId,
        nodeId: elementNodeId('task', assignment.taskDefinitionId),
        dependencies: dependenciesOf(assignment),
        lane: laneOf(assignment),
      })),
      // Not a boundary event: it is reached by its task being active, never by the start, and leads nowhere
      // unless something names it — in which case that is the sink.
      ...events
        .filter((event) => !boundaryIds.has(event.id))
        .map((event) => ({ id: event.id, nodeId: elementNodeId('event', event.id), dependencies: eventDependenciesOf(event), lane: eventLaneOf(event) })),
    ];
    const roots = flowItems.filter((item) => item.dependencies.length === 0);
    const dependedOnIds = new Set(flowItems.flatMap((item) => item.dependencies));
    const sinks = flowItems.filter((item) => !dependedOnIds.has(item.id));

    const startLane = lanes ? (roots[0] ?? flowItems[0]).lane : undefined;
    const startEvents = (workflow.startEvents ?? []).filter((event) => !!event.id);
    startEvents.forEach((event) => {
      builder.addNode(startEventNode(event, startLane));
      roots.forEach((root) => builder.addEdge(elementNodeId('start', event.id), root.nodeId, 'sequence'));
    });

    if (sinks.length > 0) {
      builder.addNode(endEventNode(endEventLabel, lanes ? sinks.at(-1)?.lane : undefined));
      sinks.forEach((sink) => builder.addEdge(sink.nodeId, END_EVENT_NODE_ID, 'sequence'));
    }
    // endregion

    // region sequence — the flow only declaration order states
    // Tasks only, as in the engine: an intermediate event has no `parallel` and is never a task's sibling.
    implicitChains(assignments).forEach(([earlier, later]) =>
      builder.addEdge(elementNodeId('task', earlier.taskDefinitionId), elementNodeId('task', later.taskDefinitionId), 'implicit'),
    );
    // endregion

    // region data
    // An artifact an event waits for feeds that event, not the roots after it: which event a document
    // starts is the fact worth drawing, and a root task's own inputs already say what it reads. In the
    // state the event requires, which is the node the first task reads when its input state is the same.
    if (data) {
      startEvents.forEach((event) =>
        (event.requiredArtifacts ?? []).forEach((required) => {
          const artifactId = referenceIdOf(required.artifactDefinitionId);
          if (!artifactId) return;
          const object = objectOf(artifactId, stateName(required.state));
          builder.addNode(object);
          builder.addEdge(object.id, elementNodeId('start', event.id), 'start');
        }),
      );

      assignments.forEach((assignment) => {
        const definition = tasksById.get(assignment.taskDefinitionId);
        if (!definition) return;
        const taskNodeId = elementNodeId('task', assignment.taskDefinitionId);
        const states = artifactStatesOf(assignment);
        // Through `toReferenceIds`, because a RELATED_ENTITIES control writes whole entities into its form
        // control: an edited task holds ids for what the server sent and objects for what was just picked.
        toReferenceIds(definition.inputs).forEach((artifactId) => {
          const object = objectOf(artifactId, stateName(states.get(artifactId)?.inputState));
          builder.addNode(object);
          builder.addEdge(object.id, taskNodeId, 'input');
        });
        toReferenceIds(definition.outputs).forEach((artifactId) => {
          const object = objectOf(artifactId, stateName(states.get(artifactId)?.outputState));
          builder.addNode(object);
          builder.addEdge(taskNodeId, object.id, 'output');
        });
      });
    }
    // endregion

    // region tools
    if (withTools) {
      assignments.forEach((assignment) => {
        const definition = tasksById.get(assignment.taskDefinitionId);
        if (!definition) return;
        const taskNodeId = elementNodeId('task', assignment.taskDefinitionId);
        definition.steps.filter(isToolCall).forEach((step) => {
          const toolId = step.toolDefinitionId as string;
          builder.addNode(toolNode(toolId, toolsById.get(toolId)));
          // The operation is what the step actually calls, and a tool has several — so it is worth more on
          // the edge than the tool's own name, which the node already carries.
          builder.addEdge(taskNodeId, elementNodeId('tool', toolId), 'tool', step.toolOperation);
        });
      });
    }
    // endregion

    return builder.build();
  }
}

// region private helper functions
/** The lane a task with no stated performer goes in — and the one a dangling dependency goes in too. */
const UNASSIGNED_ROLE_ID = '';

/** The one end event's node id. Fixed, because there is only ever one and the model gives it no id. */
const END_EVENT_NODE_ID = elementNodeId('end', 'end');

/**
 * Which ports each relation leaves and enters by.
 *
 * Pinned rather than left to ng-diagram, because the two families of relation run in different directions
 * on this diagram and would otherwise compete for the same anchors. The flow runs left to right along the
 * lanes; artifacts and tools sit in a strip *below* them, so their lines are vertical. Unpinned, a data
 * line could leave a task by its right edge and cross the whole chain to reach the node under it.
 */
const RELATION_PORTS: Record<WorkflowRelation, { sourcePort: string; targetPort: string }> = {
  sequence: { sourcePort: 'port-right', targetPort: 'port-left' },
  implicit: { sourcePort: 'port-right', targetPort: 'port-left' },
  // Upwards out of the strip into the lanes.
  input: { sourcePort: 'port-top', targetPort: 'port-bottom' },
  start: { sourcePort: 'port-top', targetPort: 'port-bottom' },
  // Downwards out of the lanes into the strip.
  output: { sourcePort: 'port-bottom', targetPort: 'port-top' },
  tool: { sourcePort: 'port-bottom', targetPort: 'port-top' },
};

/**
 * Accumulates the graph, holding the two rules every perspective of this modeler obeys: a node id is drawn
 * once however many times it is referenced, and a pair of ends carries one edge — the first claimed.
 */
class GraphBuilder {
  private readonly nodes: WorkflowNode[] = [];
  private readonly nodeIds = new Set<string>();
  private readonly edges: WorkflowEdge[] = [];
  private readonly edgeIds = new Set<string>();

  addNode(node: WorkflowNode): void {
    if (this.nodeIds.has(node.id)) return;
    this.nodeIds.add(node.id);
    this.nodes.push(node);
  }

  /** `sourcePort` overrides the relation's own, for the one edge that leaves its node differently. */
  addEdge(source: string, target: string, relation: WorkflowRelation, label?: string, sourcePort?: string): void {
    const id = elementEdgeId(source, target);
    if (this.edgeIds.has(id)) return;
    this.edgeIds.add(id);
    const ports = { ...RELATION_PORTS[relation], ...(sourcePort ? { sourcePort } : {}) };
    this.edges.push({ id, source, target, type: WORKFLOW_RELATION_EDGE_TYPE, ...ports, data: { relation, label } });
  }

  build(): WorkflowGraph {
    // Only edges whose ends are both drawn. Nothing should produce one that is not — every reference adds
    // its node first — but an edge into nothing is a line to the origin, which reads as a real relation.
    return { nodes: this.nodes, edges: this.edges.filter((edge) => this.nodeIds.has(edge.source) && this.nodeIds.has(edge.target)) };
  }
}

/**
 * Joins a `dependsOn` list into the key its sibling level is bucketed by. NUL, because it is the one
 * character an id cannot contain — with any printable separator, `['a-b']` and `['a', 'b']` could collide
 * and two tasks the engine treats as unrelated would be chained.
 *
 * Built rather than written as an escape, so the file stays plain ASCII.
 */
const LEVEL_KEY_SEPARATOR = String.fromCharCode(0);

/** The role whose lane a task belongs in. Blank `performedBy` is the unassigned lane, not no lane. */
function laneOf(assignment: WorkflowTaskAssignment): string {
  return assignment.performedBy || UNASSIGNED_ROLE_ID;
}

/**
 * What a task waits for. `dependsOn` is the one collection of the workflow model that may be absent rather
 * than empty, and `toReferenceIds` is what flattens whatever the TAGS control left in it.
 *
 * A task naming *itself* is dropped. It is reachable by one keystroke in a free TAGS control, dagre
 * silently swallows the self-edge while leaving it in the edge list, and ng-diagram then draws a degenerate
 * line from a node to itself — which looks like a modelled loop rather than the typo it is. The task is
 * still drawn; only the impossible dependency is not.
 */
function dependenciesOf(assignment: WorkflowTaskAssignment): string[] {
  return toReferenceIds(assignment.dependsOn).filter((dependencyId) => dependencyId !== assignment.taskDefinitionId);
}

/**
 * What an intermediate event waits for — {@link dependenciesOf}'s rules, for an `EventUse`. Nothing, for a
 * boundary event: it takes no `dependsOn`, and one a form still holds would draw an edge into the border of
 * a task, which the engine never follows.
 */
function eventDependenciesOf(event: EventUse): string[] {
  if (hostOf(event) !== undefined) return [];
  return toReferenceIds(event.dependsOn).filter((dependencyId) => dependencyId !== event.id);
}

/** The task a boundary event is attached to, or `undefined` for an intermediate one. Blank is no host. */
function hostOf(event: EventUse): string | undefined {
  return event.attachedTo?.trim() || undefined;
}

/** One node of the flow as its roots and sinks are found — a task or an intermediate event. */
interface FlowItem {
  id: string;
  nodeId: string;
  dependencies: string[];
  lane: string;
}

/**
 * The lane each intermediate event is drawn in. An event has no performer, so it borrows a neighbour's:
 * its first dependency's, which is where the flow arrives from; lacking any, its first dependent's, which
 * is where the flow goes on; lacking both, the first task's. A dependency on another event lends that
 * event's own lane, resolved the same way; a dangling one the unassigned lane, where it is drawn.
 *
 * Memoised, and guarded against a cycle of events — which the backend refuses but a form may hold — by
 * treating an event still being resolved as having no lane to lend.
 */
function eventLanes(assignments: WorkflowTaskAssignment[], events: EventUse[]): (event: EventUse) => string {
  const assignmentsById = new Map(assignments.map((assignment) => [assignment.taskDefinitionId, assignment]));
  const eventsById = new Map(events.map((event) => [event.id, event]));
  const resolved = new Map<string, string>();
  const resolving = new Set<string>();

  const laneOfDependency = (id: string): string | undefined => {
    const assignment = assignmentsById.get(id);
    if (assignment) return laneOf(assignment);
    const event = eventsById.get(id);
    return event ? resolve(event) : UNASSIGNED_ROLE_ID;
  };

  const laneOfDependents = (eventId: string): string | undefined =>
    firstDefined([
      ...assignments.filter((assignment) => dependenciesOf(assignment).includes(eventId)).map(laneOf),
      ...events.filter((other) => eventDependenciesOf(other).includes(eventId)).map(resolve),
    ]);

  function resolve(event: EventUse): string | undefined {
    if (resolved.has(event.id)) return resolved.get(event.id);
    if (resolving.has(event.id)) return undefined;
    // A boundary event is drawn on its task, so it is in the task's lane — the unassigned one for a dangling
    // task, which is where that is drawn.
    const host = hostOf(event);
    if (host !== undefined) {
      const assignment = assignmentsById.get(host);
      return assignment ? laneOf(assignment) : UNASSIGNED_ROLE_ID;
    }
    resolving.add(event.id);
    const lane = firstDefined(eventDependenciesOf(event).map(laneOfDependency)) ?? laneOfDependents(event.id);
    resolving.delete(event.id);
    if (lane !== undefined) resolved.set(event.id, lane);
    return lane;
  }

  return (event) => resolve(event) ?? (assignments.length > 0 ? laneOf(assignments[0]) : UNASSIGNED_ROLE_ID);
}

/** The first of the values that is set. */
function firstDefined(values: (string | undefined)[]): string | undefined {
  return values.find((value) => value !== undefined);
}

/** The list with duplicates removed, first occurrence winning — so lanes are drawn in first-task order. */
function distinct(values: string[]): string[] {
  return [...new Set(values)];
}

/**
 * Consecutive pairs of tasks that run one after the other only because of the order they are declared in.
 *
 * Two rules, and both are the engine's rather than this converter's — a diagram that inferred an order the
 * engine does not enforce would be worse than one that inferred none. Both are read off
 * `TaskActivationService.hasActiveSiblingAtSameLevel`, which is the whole of the rule server-side:
 *
 * **Siblings are tasks whose `dependsOn` lists are equal *as lists*.** The engine compares them with
 * `List.equals`, which is order-sensitive, so `[a, b]` and `[b, a]` are two different levels to it however
 * alike they read. Keyed on the raw order for that reason — sorting first would group tasks the engine
 * treats as unrelated and draw a chain that never happens. Joined on NUL, the one character an id cannot
 * contain, so `[ab]` and `[a, b]` cannot collide either.
 *
 * **`parallel: true` opts a task out of the chain entirely**, not merely out of its own link: the engine
 * filters `!sibling.parallel()` on both sides of the comparison, so for a level of `A(false)`, `B(true)`,
 * `C(false)` the chain is `A → C` and B runs beside both.
 */
function implicitChains(assignments: WorkflowTaskAssignment[]): [WorkflowTaskAssignment, WorkflowTaskAssignment][] {
  const bySiblingGroup = new Map<string, WorkflowTaskAssignment[]>();
  assignments
    .filter((assignment) => !assignment.parallel)
    .forEach((assignment) => {
      const key = dependenciesOf(assignment).join(LEVEL_KEY_SEPARATOR);
      bySiblingGroup.set(key, [...(bySiblingGroup.get(key) ?? []), assignment]);
    });

  return [...bySiblingGroup.values()].flatMap((siblings) =>
    siblings.slice(1).map((later, index): [WorkflowTaskAssignment, WorkflowTaskAssignment] => [siblings[index], later]),
  );
}

/** One reference as its id — a `FOREIGN_KEY` control may have written the picked entity itself. */
function referenceIdOf(reference: EntityReference | undefined): string | undefined {
  return reference === undefined ? undefined : toReferenceIds([reference])[0];
}

/** A stated state, or `undefined` for a blank one — a cleared text box writes `''`, which is no state. */
function stateName(state: string | null | undefined): string | undefined {
  return state?.trim() || undefined;
}

/**
 * The assignment's artifact states by artifact id. The first row for an artifact wins, as the backend
 * refuses a second one anyway; the list may be absent on an assignment built before the field existed.
 */
function artifactStatesOf(assignment: WorkflowTaskAssignment): Map<string, TaskArtifactState> {
  const byArtifactId = new Map<string, TaskArtifactState>();
  (assignment.artifactStates ?? []).forEach((state) => {
    const artifactId = referenceIdOf(state.artifactDefinitionId);
    if (artifactId && !byArtifactId.has(artifactId)) byArtifactId.set(artifactId, state);
  });
  return byArtifactId;
}

/** Whether completing a step is a call the engine makes, and to something it can name. */
function isToolCall(step: StepDefinition): boolean {
  return step.stepType === TaskStepType.SERVICE_STEP && !!step.toolDefinitionId;
}

/**
 * One lane. A group node rather than an element card: it is a band as wide as the diagram, and ng-diagram
 * resolves a group through a template of its own shape.
 *
 * No `autoSize`, unlike every element node — a group does not grow to its children in ng-diagram, and the
 * swimlane layout is what computes the box that contains them. The position is a placeholder for the same
 * reason every other node's is.
 */
function laneNode(roleId: string, role: RoleDefinition | undefined, unassignedLabel: string): WorkflowNode {
  return {
    id: laneNodeId(roleId),
    type: WORKFLOW_LANE_TYPE,
    isGroup: true,
    highlighted: false,
    position: { x: 0, y: 0 },
    data: {
      kind: 'role',
      elementId: roleId,
      label: roleId === UNASSIGNED_ROLE_ID ? unassignedLabel : role?.name || roleId,
      description: role?.description,
      unresolved: roleId === UNASSIGNED_ROLE_ID || role === undefined,
    },
  };
}

/**
 * One task of the workflow. `definition` is the catalog entry when there is one; there need not be, since
 * both `taskDefinitionId` and every `dependsOn` entry are plain ids nothing enforces the resolution of.
 */
function taskNode(taskId: string, definition: TaskDefinition | undefined, laneRoleId: string | undefined): WorkflowNode {
  return {
    id: elementNodeId('task', taskId),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    ...(laneRoleId === undefined ? {} : { groupId: laneNodeId(laneRoleId) }),
    data: {
      kind: 'task',
      elementId: taskId,
      label: definition ? definition.name || taskId : taskId,
      description: definition?.description,
      unresolved: definition === undefined,
      ...(definition
        ? {
            task: {
              preconditionRuleId: definition.preconditionRuleId || undefined,
              postconditionRuleId: definition.postconditionRuleId || undefined,
              steps: definition.steps.map((step) => step.name || step.id),
            },
          }
        : {}),
    },
  };
}

/**
 * One start event, labelled by what most plainly says what starts the workflow: its name; lacking one, the
 * event it waits for when it is a `TRIGGERING_EVENT`; lacking that, how it fires. The start type is always
 * the description — the node's tooltip — unless it already is the label.
 */
function startEventNode(event: StartEvent, laneRoleId: string | undefined): WorkflowNode {
  const eventType = event.startType === WorkflowStartConditionType.TRIGGERING_EVENT ? event.eventType : undefined;
  const label = event.name || eventType || event.startType || event.id;
  return {
    id: elementNodeId('start', event.id),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    size: { ...EVENT_NODE_SIZE },
    autoSize: false,
    ...(laneRoleId === undefined ? {} : { groupId: laneNodeId(laneRoleId) }),
    data: {
      kind: 'start',
      elementId: event.id,
      label,
      description: label === event.startType ? undefined : event.startType,
    },
  };
}

/**
 * One intermediate or boundary event, labelled by its name or, lacking one, by the catalog event it throws
 * or catches — which is otherwise the description, the node's tooltip. The direction picks the symbol, and
 * a timer the clock; a timer's description is when it fires, `DURATION PT2H`.
 *
 * A boundary event is the smaller circle, carries its host's node id for the layout to pin it by, and is
 * ordered above its siblings so that the half of it overlapping the task card is drawn over the card.
 */
function intermediateEventNode(event: EventUse, laneRoleId: string | undefined): WorkflowNode {
  const timer = timerOf(event);
  const host = hostOf(event);
  const label = event.name || event.eventDefinitionId || event.id;
  const fires = timer ? `${timer.type} ${timer.expression}` : event.eventDefinitionId || undefined;
  return {
    id: elementNodeId('event', event.id),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    size: { ...(host === undefined ? EVENT_NODE_SIZE : BOUNDARY_EVENT_NODE_SIZE) },
    autoSize: false,
    ...(host === undefined ? {} : { zOrder: 1 }),
    ...(laneRoleId === undefined ? {} : { groupId: laneNodeId(laneRoleId) }),
    data: {
      kind: 'event',
      elementId: event.id,
      label,
      description: label === fires ? undefined : fires,
      // A boundary event can only catch, so a row not given a direction yet is drawn as the catch it will be.
      direction: event.direction ?? (host === undefined ? undefined : EventDirection.CATCH),
      ...(timer ? { timer: true } : {}),
      ...(host === undefined ? {} : { attachedTo: elementNodeId('task', host), interrupting: event.interrupting !== false }),
    },
  };
}

/** The derived end event. No `elementId`: there is no row behind it to name. */
function endEventNode(label: string, laneRoleId: string | undefined): WorkflowNode {
  return {
    id: END_EVENT_NODE_ID,
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    size: { ...EVENT_NODE_SIZE },
    autoSize: false,
    ...(laneRoleId === undefined ? {} : { groupId: laneNodeId(laneRoleId) }),
    data: { kind: 'end', label },
  };
}

/**
 * One artifact a task reads or writes, or a start event waits for. Outside every lane — see the layout.
 *
 * Drawn as the *object* flowing through this workflow, in UML's object notation: `new_order : Order`, the
 * name from the workflow's `ArtifactUse.objectName`, anonymous `:Order` without one. With a state —
 * `new_order : Order [DRAFT]` — it is the object in that state, a node of its own per state as in a UML
 * activity diagram: the id carries the state, so every task reading or writing the object in that state
 * meets in it. `elementId` stays the artifact's, so every one of them navigates to the same definition.
 */
function artifactNode(artifactId: string, artifact: ArtifactDefinition | undefined, objectName?: string, state?: string): WorkflowNode {
  const className = artifact ? artifact.name || artifactId : artifactId;
  const objectLabel = objectName ? `${objectName} : ${className}` : `:${className}`;
  return {
    id: elementNodeId('artifact', state ? `${artifactId}[${state}]` : artifactId),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    data: {
      kind: 'artifact',
      elementId: artifactId,
      label: state ? `${objectLabel} [${state}]` : objectLabel,
      description: artifact?.description,
      unresolved: artifact === undefined,
    },
  };
}

/** One external system a service step calls. */
function toolNode(toolId: string, tool: ToolDefinition | undefined): WorkflowNode {
  return {
    id: elementNodeId('tool', toolId),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    data: {
      kind: 'tool',
      elementId: toolId,
      label: tool ? tool.name || toolId : toolId,
      description: tool?.description,
      unresolved: tool === undefined,
    },
  };
}
// endregion
