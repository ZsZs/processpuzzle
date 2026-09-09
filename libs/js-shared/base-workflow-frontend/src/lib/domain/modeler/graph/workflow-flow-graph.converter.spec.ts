import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { StepDefinition, TaskDefinition, TaskStepType } from '../../definition/task-definition';
import { ToolDefinition } from '../../definition/tool-definition';
import { JoinType, RequiredStartArtifact, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from '../../definition/workflow';
import { elementEdgeId, elementNodeId, isLaneNode, laneNodeId, WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE, WorkflowGraph } from '../workflow-graph';
import { WorkflowFlowGraphConverter, WorkflowFlowGraphOptions } from './workflow-flow-graph.converter';

/**
 * The seeded `order-fulfillment-workflow` and the four catalogs it composes — entities, because that is what
 * a store holds and what the converter is handed.
 *
 * Built with the constructors rather than out of the `test-*.ts` wire fixtures, following
 * `role-responsibility-graph.converter.spec.ts`: those fixtures are the shapes a *mapper* is given, and
 * casting them into entities here would assert the mapper's job as well as this converter's. The ids, names
 * and relations are the seed's
 * (`base-workflow-backend/.../default-workflows/processpuzzle-testbed-workflows.yaml`), so a graph that is
 * right here is a graph that would be right against a running testbed.
 *
 * The shape: a three-task linear chain — `review-order` performed by the clerk, `approve-shipment` by the
 * manager, `confirm-delivery` back to the clerk. Two lanes, and the chain crosses between them twice.
 */
const ROLES = [
  new RoleDefinition({ id: 'clerk', name: 'Order Clerk', description: 'Enters orders.', responsibleFor: ['order-entity'] }),
  new RoleDefinition({ id: 'manager', name: 'Order Manager', responsibleFor: ['fulfillment-invoice'] }),
];

const ARTIFACTS = [
  new ArtifactDefinition({ id: 'order-entity', name: 'Order', description: 'The order.', artifactType: ArtifactType.ENTITY }),
  new ArtifactDefinition({ id: 'fulfillment-invoice', name: 'Fulfillment Invoice', artifactType: ArtifactType.DOCUMENT }),
];

const TASKS = [
  new TaskDefinition({
    id: 'review-order',
    name: 'Review Order',
    description: 'Review order details.',
    performedByRoles: ['clerk', 'manager'],
    inputs: ['order-entity'],
    outputs: ['order-entity'],
    steps: [new StepDefinition({ id: 'check-items', name: 'Check Line Items', stepType: TaskStepType.SERVICE_STEP, toolDefinitionId: 'automated-check-tool', toolOperation: 'inventory-check' })],
  }),
  new TaskDefinition({ id: 'approve-shipment', name: 'Approve Shipment', performedByRoles: ['manager'], inputs: ['order-entity'], outputs: ['order-entity'] }),
  new TaskDefinition({
    id: 'confirm-delivery',
    name: 'Confirm Delivery',
    performedByRoles: ['clerk'],
    inputs: ['order-entity'],
    outputs: ['fulfillment-invoice'],
    steps: [new StepDefinition({ id: 'generate-invoice', name: 'Generate Invoice', stepType: TaskStepType.SERVICE_STEP, toolDefinitionId: 'automated-check-tool', toolOperation: 'generate-doc' })],
  }),
];

const TOOLS = [new ToolDefinition({ id: 'automated-check-tool', name: 'Automated Check Tool', baseUrl: 'https://checks.example.com' })];

const LABELS = { unassignedLane: 'Unassigned', anyJoin: 'any' };

/**
 * One task assignment. Through the class rather than as a literal, so a field added to the contract arrives
 * here as its declared default instead of as a compile error in twenty places.
 */
function assignment(init: Partial<WorkflowTaskAssignment>): WorkflowTaskAssignment {
  return new WorkflowTaskAssignment(init);
}

/** The seeded workflow. `startCondition` arrives flattened, which is what `WorkflowMapper` produces. */
function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return new Workflow({
    id: 'order-fulfillment-workflow',
    name: 'Order Fulfillment Workflow',
    startType: WorkflowStartConditionType.INPUT_ARTIFACT,
    requiredArtifacts: [new RequiredStartArtifact({ artifactDefinitionId: 'order-entity', state: 'DRAFT' })],
    roles: [{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }],
    artifacts: [{ artifactDefinitionId: 'order-entity' }, { artifactDefinitionId: 'fulfillment-invoice' }],
    tools: [{ toolDefinitionId: 'automated-check-tool' }],
    tasks: [
      assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [] }),
      assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], joinType: JoinType.ALL }),
      assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['approve-shipment'] }),
    ],
    ...overrides,
  });
}

function convert(target = workflow(), options: WorkflowFlowGraphOptions = {}): WorkflowGraph {
  return WorkflowFlowGraphConverter.toGraph(target, TASKS, ROLES, ARTIFACTS, TOOLS, { labels: LABELS, ...options });
}

/** Node ids, so an expectation reads as the set of things drawn rather than as a wall of objects. */
function nodeIds(graph: WorkflowGraph): string[] {
  return graph.nodes.map((node) => node.id);
}

function edgesOfRelation(graph: WorkflowGraph, relation: string) {
  return graph.edges.filter((edge) => edge.data?.relation === relation);
}

const REVIEW = elementNodeId('task', 'review-order');
const APPROVE = elementNodeId('task', 'approve-shipment');
const CONFIRM = elementNodeId('task', 'confirm-delivery');
const ORDER = elementNodeId('artifact', 'order-entity');
const INVOICE = elementNodeId('artifact', 'fulfillment-invoice');
const CHECK_TOOL = elementNodeId('tool', 'automated-check-tool');

describe('WorkflowFlowGraphConverter', () => {
  describe('nothing to draw', () => {
    // The tab renders no canvas at all in these two states — but the converter is what it asks, so it has
    // to answer rather than throw on the workflow it has not loaded yet.
    it('converts an absent workflow to an empty graph', () => {
      expect(WorkflowFlowGraphConverter.toGraph(undefined, TASKS, ROLES, ARTIFACTS, TOOLS)).toEqual({ nodes: [], edges: [] });
    });

    it('converts a workflow with no tasks to an empty graph', () => {
      expect(convert(workflow({ tasks: [] }))).toEqual({ nodes: [], edges: [] });
    });
  });

  describe('the flow', () => {
    it('draws one task node per assignment, named from the task catalog', () => {
      const graph = convert();

      expect(nodeIds(graph)).toContain(REVIEW);
      expect(graph.nodes.find((node) => node.id === REVIEW)?.data).toMatchObject({ kind: 'task', label: 'Review Order', unresolved: false });
    });

    /**
     * The single most reversible thing in this converter. `dependsOn` names what must finish *first*, so an
     * edge runs from the dependency to the task naming it — the opposite of the field's direction.
     */
    it('runs a sequence edge from the dependency to the task that names it', () => {
      const graph = convert();

      expect(edgesOfRelation(graph, 'sequence').map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, APPROVE), elementEdgeId(APPROVE, CONFIRM)]);
    });

    it('gives every edge the relation template and the ports its direction needs', () => {
      const sequence = edgesOfRelation(convert(), 'sequence')[0];

      expect(sequence.type).toBe(WORKFLOW_RELATION_EDGE_TYPE);
      expect(sequence.sourcePort).toBe('port-right');
      expect(sequence.targetPort).toBe('port-left');
    });

    // The model's only gateway. It qualifies the whole `dependsOn` set, so it is written on each edge of it.
    it('marks the edges of an ANY join, and only when there is a choice to make', () => {
      const anyJoin = convert(
        workflow({ tasks: [...workflow().tasks.slice(0, 2), assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['review-order', 'approve-shipment'], joinType: JoinType.ANY })] }),
      );

      expect(edgesOfRelation(anyJoin, 'sequence').filter((edge) => edge.target === CONFIRM).map((edge) => edge.data?.label)).toEqual(['any', 'any']);
    });

    it('leaves a single-dependency ANY join unlabelled — there is nothing to choose between', () => {
      const graph = convert(workflow({ tasks: [workflow().tasks[0], assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], joinType: JoinType.ANY })] }));

      expect(edgesOfRelation(graph, 'sequence').map((edge) => edge.data?.label)).toEqual([undefined]);
    });

    /**
     * The ordering that exists nowhere as data: two tasks waiting on the same thing, neither marked
     * parallel, run in the order they are declared. Drawn as its own relation so it is distinguishable from
     * a dependency the author actually stated.
     */
    it('chains non-parallel siblings in declaration order', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: false }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: [], parallel: false }),
            assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: [], parallel: false }),
          ],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit').map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, APPROVE), elementEdgeId(APPROVE, CONFIRM)]);
    });

    it('leaves parallel siblings unchained — that is what the flag says', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: true }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: [], parallel: true }),
          ],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit')).toEqual([]);
    });

    it('chains two tasks waiting on the same predecessor', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: false }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], parallel: false }),
            assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['review-order'], parallel: false }),
          ],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit').map((edge) => edge.id)).toEqual([elementEdgeId(APPROVE, CONFIRM)]);
    });

    /**
     * Mirrors the engine rather than reading the lists charitably. `TaskActivationService` compares them
     * with `List.equals`, which is order-sensitive, so two differently-ordered lists are two levels and
     * neither task waits on the other. Grouping them would draw a sequence that never happens.
     */
    it('treats differently ordered dependency lists as different levels, as the engine does', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: ['a', 'b'], parallel: false }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['b', 'a'], parallel: false }),
          ],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit')).toEqual([]);
    });

    it('skips a parallel task without breaking the chain around it', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: false }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: [], parallel: true }),
            assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: [], parallel: false }),
          ],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit').map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, CONFIRM)]);
    });

    /**
     * One keystroke away in a free TAGS control, and the worst possible thing to draw: dagre swallows a
     * self-edge while leaving it in the edge list, and ng-diagram then renders a loop that reads as
     * modelled rather than mistyped.
     */
    it('drops a task that depends on itself, keeping the task', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: ['review-order'], parallel: false })] }));

      expect(nodeIds(graph)).toContain(REVIEW);
      expect(graph.edges.filter((edge) => edge.source === edge.target)).toEqual([]);
      expect(edgesOfRelation(graph, 'sequence')).toEqual([]);
    });
  });

  describe('lanes', () => {
    it('draws one lane per performing role, in the order the workflow reaches them', () => {
      const graph = convert();

      expect(graph.nodes.filter((node) => isLaneNode(node)).map((node) => node.id)).toEqual([laneNodeId('clerk'), laneNodeId('manager')]);
    });

    it('names a lane from the role catalog and draws it as a group', () => {
      const lane = convert().nodes.find((node) => node.id === laneNodeId('clerk'));

      expect(lane && isLaneNode(lane)).toBe(true);
      expect(lane?.type).toBe(WORKFLOW_LANE_TYPE);
      expect(lane?.data).toMatchObject({ kind: 'role', label: 'Order Clerk', unresolved: false });
    });

    it('puts each task in the lane of the role performing it here', () => {
      const graph = convert();

      expect(graph.nodes.find((node) => node.id === REVIEW)?.groupId).toBe(laneNodeId('clerk'));
      expect(graph.nodes.find((node) => node.id === APPROVE)?.groupId).toBe(laneNodeId('manager'));
    });

    // A lane is ordered before the tasks it holds, so the band cannot be painted over its own contents.
    it('orders every lane ahead of the tasks it contains', () => {
      const graph = convert();
      const lastLane = graph.nodes.map(isLaneNode).lastIndexOf(true);
      const firstTask = graph.nodes.findIndex((node) => node.data.kind === 'task');

      expect(lastLane).toBeLessThan(firstTask);
    });

    it('collapses to a flat flow when lanes are off', () => {
      const graph = convert(workflow(), { lanes: false });

      expect(graph.nodes.filter((node) => isLaneNode(node))).toEqual([]);
      expect(graph.nodes.every((node) => node.groupId === undefined)).toBe(true);
      // The flow itself is untouched — only the grouping went away.
      expect(edgesOfRelation(graph, 'sequence')).toHaveLength(2);
    });
  });

  describe('the data layer', () => {
    it('draws an artifact a task reads as an edge into the task', () => {
      const graph = convert();

      expect(nodeIds(graph)).toContain(ORDER);
      expect(edgesOfRelation(graph, 'input').map((edge) => edge.id)).toContain(elementEdgeId(ORDER, APPROVE));
    });

    it('draws an artifact a task writes as an edge out of the task', () => {
      expect(edgesOfRelation(convert(), 'output').map((edge) => edge.id)).toContain(elementEdgeId(CONFIRM, INVOICE));
    });

    it('runs data edges vertically, so they do not compete with the chain for an anchor', () => {
      const input = edgesOfRelation(convert(), 'input')[0];

      expect(input.sourcePort).toBe('port-top');
      expect(input.targetPort).toBe('port-bottom');
    });

    /**
     * The nearest honest thing to a start element: the contract has none, and a root task is only a root
     * because nothing depends on it. The required artifact's state is what the edge says.
     */
    it('feeds the required start artifacts into every task that depends on nothing', () => {
      const start = edgesOfRelation(convert(), 'start');

      expect(start.map((edge) => edge.id)).toEqual([elementEdgeId(ORDER, REVIEW)]);
      expect(start[0].data?.label).toBe('DRAFT');
    });

    it('draws no artifact and no data edge when the layer is off', () => {
      const graph = convert(workflow(), { data: false });

      expect(graph.nodes.filter((node) => node.data.kind === 'artifact')).toEqual([]);
      expect([...edgesOfRelation(graph, 'input'), ...edgesOfRelation(graph, 'output'), ...edgesOfRelation(graph, 'start')]).toEqual([]);
    });
  });

  describe('the tool layer', () => {
    it('draws the tool a service step calls, labelled by the operation', () => {
      const graph = convert();
      const toolEdges = edgesOfRelation(graph, 'tool');

      expect(nodeIds(graph)).toContain(CHECK_TOOL);
      expect(toolEdges.map((edge) => [edge.id, edge.data?.label])).toEqual([
        [elementEdgeId(REVIEW, CHECK_TOOL), 'inventory-check'],
        [elementEdgeId(CONFIRM, CHECK_TOOL), 'generate-doc'],
      ]);
    });

    // A USER_STEP names no tool, and a SERVICE_STEP that names none has nothing to call.
    it('ignores a step that is not a resolvable tool call', () => {
      const userStepOnly = TASKS.map((task) => new TaskDefinition({ ...task, steps: task.steps.map((step) => ({ ...step, stepType: undefined })) }));
      const graph = WorkflowFlowGraphConverter.toGraph(workflow(), userStepOnly, ROLES, ARTIFACTS, TOOLS, { labels: LABELS });

      expect(edgesOfRelation(graph, 'tool')).toEqual([]);
      expect(graph.nodes.filter((node) => node.data.kind === 'tool')).toEqual([]);
    });

    it('draws no tool and no tool edge when the layer is off', () => {
      const graph = convert(workflow(), { tools: false });

      expect(graph.nodes.filter((node) => node.data.kind === 'tool')).toEqual([]);
      expect(edgesOfRelation(graph, 'tool')).toEqual([]);
    });
  });

  describe('references that resolve to nothing', () => {
    /**
     * `dependsOn` is authored through a free TAGS control — it names sibling rows of the list being edited,
     * so no closed option list could be current — which makes an id resolving to nothing an ordinary state
     * of the model. Dropping it would show the chain as shorter than the author wrote it.
     */
    it('draws a dependency naming no assignment, labelled by the raw id', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: ['nothing-of-the-kind'], parallel: false })] }));
      const dangling = graph.nodes.find((node) => node.id === elementNodeId('task', 'nothing-of-the-kind'));

      expect(dangling?.data).toMatchObject({ kind: 'task', label: 'nothing-of-the-kind', unresolved: true });
      expect(edgesOfRelation(graph, 'sequence').map((edge) => edge.id)).toEqual([elementEdgeId(elementNodeId('task', 'nothing-of-the-kind'), REVIEW)]);
    });

    it('puts a dangling dependency in the unassigned lane — nothing says who performs it', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: ['nothing-of-the-kind'], parallel: false })] }));

      expect(graph.nodes.find((node) => node.id === elementNodeId('task', 'nothing-of-the-kind'))?.groupId).toBe(laneNodeId(''));
      expect(graph.nodes.find((node) => node.id === laneNodeId(''))?.data).toMatchObject({ label: 'Unassigned', unresolved: true });
    });

    it('adds no unassigned lane when every reference resolves', () => {
      expect(nodeIds(convert())).not.toContain(laneNodeId(''));
    });

    it('draws a task the catalog does not hold, labelled by the raw id', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'ghost-task', performedBy: 'clerk', dependsOn: [], parallel: false })] }));

      expect(graph.nodes.find((node) => node.id === elementNodeId('task', 'ghost-task'))?.data).toMatchObject({ label: 'ghost-task', unresolved: true });
    });

    it('draws a lane for a role the catalog does not hold', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'ghost-role', dependsOn: [], parallel: false })] }));

      expect(graph.nodes.find((node) => node.id === laneNodeId('ghost-role'))?.data).toMatchObject({ label: 'ghost-role', unresolved: true });
    });

    it('puts a task with no stated performer in the unassigned lane', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: '', dependsOn: [], parallel: false })] }));

      expect(graph.nodes.find((node) => node.id === REVIEW)?.groupId).toBe(laneNodeId(''));
    });
  });

  describe('what is deliberately left out', () => {
    /**
     * A parent's roles, artifacts, tools and tasks are not merged client-side, and an `override: true` row
     * only means something against a resolved parent — so drawing the parent's id as a node would suggest
     * the diagram accounted for what it inherits.
     */
    it('draws nothing for the workflow a workflow extends', () => {
      // `claim-handling-workflow` of the seed: extends the workflow above and overrides one of its tasks.
      const graph = convert(
        new Workflow({
          id: 'claim-handling-workflow',
          name: 'Claim Handling Workflow',
          extends: 'order-fulfillment-workflow',
          roles: [{ roleDefinitionId: 'clerk' }],
          tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', override: true })],
        }),
      );

      // Its own task, its own lane, and the artifact and tool that task touches — but nothing standing for
      // the parent, and none of the parent's other two tasks, which are not in this workflow's `tasks`.
      expect(graph.nodes.filter((node) => node.data.kind === 'workflow')).toEqual([]);
      expect(nodeIds(graph)).not.toContain(elementNodeId('workflow', 'order-fulfillment-workflow'));
      expect(graph.nodes.filter((node) => node.data.kind === 'task').map((node) => node.id)).toEqual([REVIEW]);
    });

    // Every element is drawn by the one element template; only a lane is drawn by a different one.
    it('draws every non-lane element through the shared element type', () => {
      expect(convert().nodes.filter((node) => !isLaneNode(node)).every((node) => node.type === WORKFLOW_NODE_TYPE)).toBe(true);
    });

    it('holds one edge per pair of ends, so no two lines are drawn on top of each other', () => {
      const graph = convert();

      expect(new Set(graph.edges.map((edge) => edge.id)).size).toBe(graph.edges.length);
    });

    it('holds no edge whose end is not drawn', () => {
      const graph = convert();
      const drawn = new Set(nodeIds(graph));

      expect(graph.edges.every((edge) => drawn.has(edge.source) && drawn.has(edge.target))).toBe(true);
    });
  });
});
