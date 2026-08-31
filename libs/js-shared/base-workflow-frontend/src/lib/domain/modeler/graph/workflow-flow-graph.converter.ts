import { ArtifactDefinition } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { StepDefinition, TaskDefinition, TaskStepType } from '../../definition/task-definition';
import { ToolDefinition } from '../../definition/tool-definition';
import { JoinType, Workflow, WorkflowTaskAssignment } from '../../definition/workflow';
import { toReferenceIds } from '../../reference-ids';
import {
  elementEdgeId,
  elementNodeId,
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
 * Which layers of the workflow are on screen, and the two strings the screen has to lend the converter.
 *
 * Every flag defaults to `true`: the whole workflow is the useful first sight of it, and a toggle exists to
 * take something *away*. They are applied here rather than in the canvas so that a hidden layer's nodes and
 * edges never reach the layout — a filtered layout re-ranks the remaining flow instead of leaving the gaps
 * the hidden nodes occupied.
 *
 * `labels` is here because this module holds no transloco. Two of the diagram's labels are not data — the
 * name of the lane for a task nobody is shown to perform, and the word marking an `ANY` join — so the
 * screen resolves them and passes them down, the same way `RoleResponsibilityGraphConverter` takes the id
 * to highlight from the screen rather than discovering it.
 */
export interface WorkflowFlowGraphOptions {
  /** Group tasks into one lane per performing role. Off draws a flat left-to-right flow. */
  lanes?: boolean;
  /** Artifact nodes, their input/output edges, and the workflow's required start artifacts. */
  data?: boolean;
  /** Tool nodes and the service steps that call them. */
  tools?: boolean;
  labels?: {
    /** Lane name for tasks with no stated performer. */
    unassignedLane?: string;
    /** Written on the incoming edges of a task whose `dependsOn` set is satisfied by any one of them. */
    anyJoin?: string;
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
 * - **The root.** A task depending on nothing is eligible from the start. There is no start element to
 *   draw, so with the data layer on the workflow's `requiredArtifacts` are drawn feeding those roots, which
 *   is the nearest honest thing to one.
 * - **The join.** `joinType: ANY` on a task with two or more dependencies is the model's only gateway. It
 *   has no element of its own, so it is written on the edges it qualifies.
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
   * screen says so in words.
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

    // Indexed once rather than searched per reference: every task names a role and artifacts, so a
    // per-reference `find` over four catalogs would be quadratic in lists that grow together.
    const tasksById = new Map(tasks.map((task) => [task.id, task]));
    const rolesById = new Map(roles.map((role) => [role.id, role]));
    const artifactsById = new Map(artifacts.map((artifact) => [artifact.id, artifact]));
    const toolsById = new Map(tools.map((tool) => [tool.id, tool]));

    const assignments = workflow.tasks;
    const assignedTaskIds = new Set(assignments.map((assignment) => assignment.taskDefinitionId));

    const builder = new GraphBuilder();

    // region tasks and their lanes
    // Every dependency naming no assignment of this workflow. Drawn rather than dropped so that the chain
    // does not simply stop, and placed in the unassigned lane because nothing says who would perform it.
    const danglingTaskIds = distinct(assignments.flatMap(dependenciesOf).filter((dependencyId) => !assignedTaskIds.has(dependencyId)));

    // Lane order is the order each role's first task appears in, dangling dependencies last — so the lane
    // the workflow starts in is the top one and the invented lane, if any, is the bottom.
    const laneRoleIds = lanes ? distinct([...assignments.map(laneOf), ...(danglingTaskIds.length > 0 ? [UNASSIGNED_ROLE_ID] : [])]) : [];
    laneRoleIds.forEach((roleId) => builder.addNode(laneNode(roleId, rolesById.get(roleId), unassignedLaneLabel)));

    assignments.forEach((assignment) =>
      builder.addNode(taskNode(assignment.taskDefinitionId, tasksById.get(assignment.taskDefinitionId), lanes ? laneOf(assignment) : undefined)),
    );
    danglingTaskIds.forEach((taskId) => builder.addNode(taskNode(taskId, undefined, lanes ? UNASSIGNED_ROLE_ID : undefined)));
    // endregion

    // region sequence — the flow the model states
    assignments.forEach((assignment) => {
      const dependencies = dependenciesOf(assignment);
      // The join is a property of the whole set, so it is written on each edge of it rather than once.
      const label = assignment.joinType === JoinType.ANY && dependencies.length > 1 ? anyJoinLabel : undefined;
      dependencies.forEach((dependencyId) =>
        builder.addEdge(elementNodeId('task', dependencyId), elementNodeId('task', assignment.taskDefinitionId), 'sequence', label),
      );
    });
    // endregion

    // region sequence — the flow only declaration order states
    implicitChains(assignments).forEach(([earlier, later]) =>
      builder.addEdge(elementNodeId('task', earlier.taskDefinitionId), elementNodeId('task', later.taskDefinitionId), 'implicit'),
    );
    // endregion

    // region data
    // Before the inputs and outputs, so that where a start artifact is also a root task's input the one
    // line they would share reads as the start. Only one edge is held per pair of ends — two lines between
    // the same two nodes would be drawn exactly on top of each other.
    if (data) {
      const rootTaskIds = assignments.filter((assignment) => dependenciesOf(assignment).length === 0).map((assignment) => assignment.taskDefinitionId);
      workflow.requiredArtifacts.forEach((required) => {
        const artifactId = required.artifactDefinitionId;
        if (!artifactId) return;
        builder.addNode(artifactNode(artifactId, artifactsById.get(artifactId)));
        rootTaskIds.forEach((taskId) => builder.addEdge(elementNodeId('artifact', artifactId), elementNodeId('task', taskId), 'start', required.state));
      });

      assignments.forEach((assignment) => {
        const definition = tasksById.get(assignment.taskDefinitionId);
        if (!definition) return;
        const taskNodeId = elementNodeId('task', assignment.taskDefinitionId);
        // Through `toReferenceIds`, because a RELATED_ENTITIES control writes whole entities into its form
        // control: an edited task holds ids for what the server sent and objects for what was just picked.
        toReferenceIds(definition.inputs).forEach((artifactId) => {
          builder.addNode(artifactNode(artifactId, artifactsById.get(artifactId)));
          builder.addEdge(elementNodeId('artifact', artifactId), taskNodeId, 'input');
        });
        toReferenceIds(definition.outputs).forEach((artifactId) => {
          builder.addNode(artifactNode(artifactId, artifactsById.get(artifactId)));
          builder.addEdge(taskNodeId, elementNodeId('artifact', artifactId), 'output');
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

  addEdge(source: string, target: string, relation: WorkflowRelation, label?: string): void {
    const id = elementEdgeId(source, target);
    if (this.edgeIds.has(id)) return;
    this.edgeIds.add(id);
    this.edges.push({ id, source, target, type: WORKFLOW_RELATION_EDGE_TYPE, ...RELATION_PORTS[relation], data: { relation, label } });
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
    },
  };
}

/** One artifact a task reads or writes, or the workflow waits for. Outside every lane — see the layout. */
function artifactNode(artifactId: string, artifact: ArtifactDefinition | undefined): WorkflowNode {
  return {
    id: elementNodeId('artifact', artifactId),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    data: {
      kind: 'artifact',
      elementId: artifactId,
      label: artifact ? artifact.name || artifactId : artifactId,
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
