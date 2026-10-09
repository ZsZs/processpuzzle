import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { StepDefinition, TaskDefinition, TaskStepType } from '../../definition/task-definition';
import { ToolDefinition } from '../../definition/tool-definition';
import {
  EventDirection,
  EventUse,
  JoinType,
  RequiredStartArtifact,
  StartEvent,
  TaskArtifactState,
  TimerType,
  Workflow,
  WorkflowStartConditionType,
  WorkflowTaskAssignment,
} from '../../definition/workflow';
import { BOUNDARY_EVENT_NODE_SIZE, elementEdgeId, elementNodeId, EVENT_NODE_SIZE, isLaneNode, laneNodeId, WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE, WorkflowGraph } from '../workflow-graph';
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
 * manager, `confirm-delivery` back to the clerk. Two lanes, and the chain crosses between them twice. The
 * order walks through its states along it: DRAFT → CONFIRMED → SHIPPED → DELIVERED.
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
    outputs: ['order-entity', 'fulfillment-invoice'],
    steps: [new StepDefinition({ id: 'generate-invoice', name: 'Generate Invoice', stepType: TaskStepType.SERVICE_STEP, toolDefinitionId: 'automated-check-tool', toolOperation: 'generate-doc' })],
  }),
];

const TOOLS = [new ToolDefinition({ id: 'automated-check-tool', name: 'Automated Check Tool', baseUrl: 'https://checks.example.com' })];

const LABELS = { unassignedLane: 'Unassigned', anyJoin: 'any', endEvent: 'End' };

/**
 * One task assignment. Through the class rather than as a literal, so a field added to the contract arrives
 * here as its declared default instead of as a compile error in twenty places.
 */
function assignment(init: Partial<WorkflowTaskAssignment>): WorkflowTaskAssignment {
  return new WorkflowTaskAssignment(init);
}

/** One row of an assignment's `artifactStates`, on the order. */
function orderState(inputState: string, outputState: string): TaskArtifactState {
  return new TaskArtifactState({ artifactDefinitionId: 'order-entity', inputState, outputState });
}

/** The seeded workflow: one start event, waiting for an order in `DRAFT`, and the order's states per task. */
function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return new Workflow({
    id: 'order-fulfillment-workflow',
    name: 'Order Fulfillment Workflow',
    startEvents: [
      new StartEvent({
        id: 'order-drafted',
        name: 'OrderCreatedEvent',
        startType: WorkflowStartConditionType.INPUT_ARTIFACT,
        requiredArtifacts: [new RequiredStartArtifact({ artifactDefinitionId: 'order-entity', state: 'DRAFT' })],
      }),
    ],
    roles: [{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }],
    artifacts: [{ artifactDefinitionId: 'order-entity', objectName: 'new_order' }, { artifactDefinitionId: 'fulfillment-invoice' }],
    tools: [{ toolDefinitionId: 'automated-check-tool' }],
    tasks: [
      assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], artifactStates: [orderState('DRAFT', 'CONFIRMED')] }),
      assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], joinType: JoinType.ALL, artifactStates: [orderState('CONFIRMED', 'SHIPPED')] }),
      assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['approve-shipment'], artifactStates: [orderState('SHIPPED', 'DELIVERED')] }),
    ],
    ...overrides,
  });
}

/** The seeded workflow with no artifact state stated anywhere — every artifact drawn once, stateless. */
function stateless(overrides: Partial<Workflow> = {}): Workflow {
  return workflow({ tasks: workflow().tasks.map((task) => assignment({ ...task, artifactStates: [] })), ...overrides });
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

/** The `sequence` edges between two tasks — the `dependsOn` entries, without the events' own edges. */
function taskSequence(graph: WorkflowGraph) {
  const taskIds = new Set(graph.nodes.filter((node) => node.data.kind === 'task').map((node) => node.id));
  return edgesOfRelation(graph, 'sequence').filter((edge) => taskIds.has(edge.source) && taskIds.has(edge.target));
}

const REVIEW = elementNodeId('task', 'review-order');
const APPROVE = elementNodeId('task', 'approve-shipment');
const CONFIRM = elementNodeId('task', 'confirm-delivery');
const ORDER = elementNodeId('artifact', 'order-entity');
const INVOICE = elementNodeId('artifact', 'fulfillment-invoice');
const DRAFT_ORDER = elementNodeId('artifact', 'order-entity[DRAFT]');
const CONFIRMED_ORDER = elementNodeId('artifact', 'order-entity[CONFIRMED]');
const SHIPPED_ORDER = elementNodeId('artifact', 'order-entity[SHIPPED]');
const DELIVERED_ORDER = elementNodeId('artifact', 'order-entity[DELIVERED]');
const CHECK_TOOL = elementNodeId('tool', 'automated-check-tool');
const ORDER_DRAFTED = elementNodeId('start', 'order-drafted');
const END = elementNodeId('end', 'end');
const PAYMENT_RECEIVED = elementNodeId('event', 'payment-received');
const ORDER_SHIPPED = elementNodeId('event', 'order-shipped');

/**
 * The seeded workflow with two intermediate events in its chain: approval waits for a caught payment after
 * the review, and the shipment is announced by a thrown event after the delivery is confirmed — the last
 * node of the flow, so it is what feeds the end.
 */
function withEvents(overrides: Partial<Workflow> = {}): Workflow {
  const [review, approve, confirm] = workflow().tasks;
  return workflow({
    tasks: [review, assignment({ ...approve, dependsOn: ['payment-received'] }), confirm],
    events: [
      new EventUse({ id: 'payment-received', name: 'Payment received', eventDefinitionId: 'PaymentReceivedEvent', direction: EventDirection.CATCH, dependsOn: ['review-order'] }),
      new EventUse({ id: 'order-shipped', eventDefinitionId: 'OrderShippedEvent', direction: EventDirection.THROW, dependsOn: ['confirm-delivery'] }),
    ],
    ...overrides,
  });
}

function nodeOf(graph: WorkflowGraph, id: string) {
  return graph.nodes.find((node) => node.id === id);
}

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

      expect(taskSequence(graph).map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, APPROVE), elementEdgeId(APPROVE, CONFIRM)]);
    });

    it('gives every edge the relation template and the ports its direction needs', () => {
      const sequence = taskSequence(convert())[0];

      expect(sequence.type).toBe(WORKFLOW_RELATION_EDGE_TYPE);
      expect(sequence.sourcePort).toBe('port-right');
      expect(sequence.targetPort).toBe('port-left');
    });

    // The model's only gateway. It qualifies the whole `dependsOn` set, so it is written on each edge of it.
    it('marks the edges of an ANY join, and only when there is a choice to make', () => {
      const anyJoin = convert(
        workflow({ tasks: [...workflow().tasks.slice(0, 2), assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['review-order', 'approve-shipment'], joinType: JoinType.ANY })] }),
      );

      expect(
        taskSequence(anyJoin)
          .filter((edge) => edge.target === CONFIRM)
          .map((edge) => edge.data?.label),
      ).toEqual(['any', 'any']);
    });

    it('leaves a single-dependency ANY join unlabelled — there is nothing to choose between', () => {
      const graph = convert(workflow({ tasks: [workflow().tasks[0], assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], joinType: JoinType.ANY })] }));

      expect(taskSequence(graph).map((edge) => edge.data?.label)).toEqual([undefined]);
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
      expect(taskSequence(graph)).toEqual([]);
    });
  });

  describe('the events', () => {
    it('draws one start node per start event, named by the event', () => {
      const start = convert().nodes.find((node) => node.id === ORDER_DRAFTED);

      expect(start?.type).toBe(WORKFLOW_NODE_TYPE);
      expect(start?.data).toMatchObject({ kind: 'start', elementId: 'order-drafted', label: 'OrderCreatedEvent', description: 'INPUT_ARTIFACT' });
    });

    // Lacking a name, the event a TRIGGERING_EVENT waits for says best what starts the workflow.
    it('labels a nameless triggering event by the event it waits for', () => {
      const graph = convert(workflow({ startEvents: [new StartEvent({ id: 'submitted', startType: WorkflowStartConditionType.TRIGGERING_EVENT, eventType: 'order.submitted' })] }));

      expect(graph.nodes.find((node) => node.id === elementNodeId('start', 'submitted'))?.data).toMatchObject({ label: 'order.submitted', description: 'TRIGGERING_EVENT' });
    });

    // An event type left over from another start type is not what fires this one, so it is not the label.
    it('ignores an event type on an event that is not a triggering one', () => {
      const graph = convert(workflow({ startEvents: [new StartEvent({ id: 'by-hand', startType: WorkflowStartConditionType.ROLE_DEFINITION, eventType: 'order.submitted' })] }));

      expect(graph.nodes.find((node) => node.id === elementNodeId('start', 'by-hand'))?.data.label).toBe('ROLE_DEFINITION');
    });

    it('prefers the name to the event type', () => {
      const graph = convert(
        workflow({ startEvents: [new StartEvent({ id: 'submitted', name: 'OrderSubmitted', startType: WorkflowStartConditionType.TRIGGERING_EVENT, eventType: 'order.submitted' })] }),
      );

      expect(graph.nodes.find((node) => node.id === elementNodeId('start', 'submitted'))?.data.label).toBe('OrderSubmitted');
    });

    // A nameless event is labelled by how it fires, the next most telling thing about it.
    it('labels a nameless start event by its start type', () => {
      const graph = convert(workflow({ startEvents: [new StartEvent({ id: 'by-hand', startType: WorkflowStartConditionType.ROLE_DEFINITION })] }));

      expect(graph.nodes.find((node) => node.id === elementNodeId('start', 'by-hand'))?.data).toMatchObject({ label: 'ROLE_DEFINITION', description: undefined });
    });

    it('runs a sequence edge from every start event to every task that depends on nothing', () => {
      const graph = convert(
        workflow({
          startEvents: [new StartEvent({ id: 'a', startType: WorkflowStartConditionType.ROLE_DEFINITION }), new StartEvent({ id: 'b', startType: WorkflowStartConditionType.TRIGGERING_EVENT })],
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: true }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: [], parallel: true }),
            assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['review-order', 'approve-shipment'] }),
          ],
        }),
      );
      const fromStart = edgesOfRelation(graph, 'sequence').filter((edge) => edge.source.startsWith('start:'));

      expect(fromStart.map((edge) => edge.id)).toEqual([
        elementEdgeId(elementNodeId('start', 'a'), REVIEW),
        elementEdgeId(elementNodeId('start', 'a'), APPROVE),
        elementEdgeId(elementNodeId('start', 'b'), REVIEW),
        elementEdgeId(elementNodeId('start', 'b'), APPROVE),
      ]);
      expect(fromStart.every((edge) => edge.sourcePort === 'port-right' && edge.targetPort === 'port-left')).toBe(true);
    });

    it('draws no start node for a workflow without start events', () => {
      expect(convert(workflow({ startEvents: [] })).nodes.filter((node) => node.data.kind === 'start')).toEqual([]);
    });

    it('skips a start event that has no id yet — it would have no node id either', () => {
      const graph = convert(workflow({ startEvents: [new StartEvent({ startType: WorkflowStartConditionType.ROLE_DEFINITION })] }));

      expect(graph.nodes.filter((node) => node.data.kind === 'start')).toEqual([]);
    });

    // Derived, since the model has none: one end, fed by every task nothing depends on.
    it('derives one end node fed by every task nothing depends on', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [] }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], parallel: true }),
            assignment({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['review-order'], parallel: true }),
          ],
        }),
      );

      expect(graph.nodes.filter((node) => node.data.kind === 'end').map((node) => node.data)).toEqual([{ kind: 'end', label: 'End' }]);
      expect(nodeOf(graph, END)?.groupId).toBe(laneNodeId('clerk'));
      expect(
        edgesOfRelation(graph, 'sequence')
          .filter((edge) => edge.target === END)
          .map((edge) => edge.source),
      ).toEqual([APPROVE, CONFIRM]);
    });

    it('closes the seeded chain on its last task', () => {
      expect(
        edgesOfRelation(convert(), 'sequence')
          .filter((edge) => edge.target === END)
          .map((edge) => edge.id),
      ).toEqual([elementEdgeId(CONFIRM, END)]);
    });

    it('draws the end even when the workflow has no start event', () => {
      expect(nodeIds(convert(workflow({ startEvents: [] })))).toContain(END);
    });

    // A cycle leaves no task undepended-on; an end with nothing leading into it would claim an exit.
    it('draws no end when every task is depended on', () => {
      const graph = convert(
        workflow({
          tasks: [
            assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: ['approve-shipment'] }),
            assignment({ taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'] }),
          ],
        }),
      );

      expect(nodeIds(graph)).not.toContain(END);
      // The start event is still drawn — it is data — but with no root there is nothing for it to feed.
      expect(nodeIds(graph)).toContain(ORDER_DRAFTED);
      expect(graph.edges.filter((edge) => edge.source === ORDER_DRAFTED && edge.data?.relation === 'sequence')).toEqual([]);
    });

    // An empty workflow still converts to nothing at all — see 'nothing to draw' — start events or not.
    it('draws neither event for a workflow with no tasks', () => {
      expect(convert(workflow({ tasks: [] })).nodes).toEqual([]);
    });

    it('puts a start event in the lane of the first root and the end in the lane of the last sink', () => {
      const graph = convert();

      expect(graph.nodes.find((node) => node.id === ORDER_DRAFTED)?.groupId).toBe(laneNodeId('clerk'));
      expect(graph.nodes.find((node) => node.id === END)?.groupId).toBe(laneNodeId('clerk'));
    });

    it('puts neither event in a lane when lanes are off', () => {
      const graph = convert(workflow(), { lanes: false });

      expect(graph.nodes.filter((node) => node.data.kind === 'start' || node.data.kind === 'end').every((node) => node.groupId === undefined)).toBe(true);
    });

    // A fixed circle rather than a measured card, so both layouts can centre it.
    it('states the event box rather than letting it be measured', () => {
      const events = convert().nodes.filter((node) => node.data.kind === 'start' || node.data.kind === 'end');

      expect(events).toHaveLength(2);
      events.forEach((node) => {
        expect(node.size).toEqual(EVENT_NODE_SIZE);
        expect(node.autoSize).toBe(false);
      });
    });
  });

  describe('intermediate events', () => {
    it('draws one event node per event use, carrying its direction', () => {
      const graph = convert(withEvents());

      expect(nodeOf(graph, PAYMENT_RECEIVED)?.data).toEqual({
        kind: 'event',
        elementId: 'payment-received',
        label: 'Payment received',
        description: 'PaymentReceivedEvent',
        direction: EventDirection.CATCH,
      });
      expect(nodeOf(graph, ORDER_SHIPPED)?.data.direction).toBe(EventDirection.THROW);
    });

    // The catalog event is then the label, and a tooltip repeating it would say nothing.
    it('labels a nameless event by the catalog event it throws or catches', () => {
      const shipped = nodeOf(convert(withEvents()), ORDER_SHIPPED);

      expect(shipped?.data.label).toBe('OrderShippedEvent');
      expect(shipped?.data.description).toBeUndefined();
    });

    it('skips an event that has no id yet', () => {
      const graph = convert(withEvents({ events: [new EventUse({ eventDefinitionId: 'OrderShippedEvent', direction: EventDirection.THROW })] }));

      expect(graph.nodes.filter((node) => node.data.kind === 'event')).toEqual([]);
    });

    // One id namespace: a `dependsOn` entry is a task or an event, whichever the workflow has by that id.
    it('runs sequence edges through the events, from a task’s or an event’s dependsOn alike', () => {
      const pairs = edgesOfRelation(convert(withEvents()), 'sequence').map((edge) => [edge.source, edge.target]);

      expect(pairs).toEqual(
        expect.arrayContaining([
          [REVIEW, PAYMENT_RECEIVED],
          [PAYMENT_RECEIVED, APPROVE],
          [CONFIRM, ORDER_SHIPPED],
        ]),
      );
      expect(pairs).not.toContainEqual([REVIEW, APPROVE]);
    });

    it('lets an event depend on another event', () => {
      const graph = convert(
        withEvents({
          events: [...withEvents().events, new EventUse({ id: 'invoice-sent', eventDefinitionId: 'InvoiceSentEvent', direction: EventDirection.THROW, dependsOn: ['order-shipped'] })],
        }),
      );

      expect(graph.edges.map((edge) => edge.id)).toContain(elementEdgeId(ORDER_SHIPPED, elementNodeId('event', 'invoice-sent')));
    });

    it('feeds the end from an event nothing depends on, and no longer from the task before it', () => {
      const feeders = convert(withEvents())
        .edges.filter((edge) => edge.target === END)
        .map((edge) => edge.source);

      expect(feeders).toEqual([ORDER_SHIPPED]);
    });

    // A catch event depending on nothing is where the flow may begin, as a task depending on nothing is.
    it('runs a start edge to an event that depends on nothing', () => {
      const [review, ...rest] = workflow().tasks;
      const graph = convert(
        workflow({
          tasks: [assignment({ ...review, dependsOn: ['go-signal'] }), ...rest],
          events: [new EventUse({ id: 'go-signal', eventDefinitionId: 'GoSignal', direction: EventDirection.CATCH })],
        }),
      );

      expect(graph.edges.filter((edge) => edge.source === ORDER_DRAFTED).map((edge) => edge.target)).toEqual([elementNodeId('event', 'go-signal')]);
    });

    it('marks the edges into an event with an ANY join', () => {
      const graph = convert(
        withEvents({
          events: [
            new EventUse({ id: 'payment-received', eventDefinitionId: 'PaymentReceivedEvent', direction: EventDirection.CATCH, dependsOn: ['review-order', 'confirm-delivery'], joinType: JoinType.ANY }),
          ],
        }),
      );

      expect(graph.edges.filter((edge) => edge.target === PAYMENT_RECEIVED).map((edge) => edge.data?.label)).toEqual(['any', 'any']);
    });

    // The engine chains siblings among tasks only: an event sharing a task's dependsOn runs beside it.
    it('chains no event into the implicit order', () => {
      const [review, approve, confirm] = workflow().tasks;
      const graph = convert(
        workflow({
          tasks: [review, approve, confirm],
          events: [new EventUse({ id: 'review-done', eventDefinitionId: 'ReviewDone', direction: EventDirection.THROW, dependsOn: ['review-order'] })],
        }),
      );

      expect(edgesOfRelation(graph, 'implicit')).toEqual([]);
    });

    it('draws a dependency of an event that names nothing as a dangling task in the unassigned lane', () => {
      const graph = convert(
        withEvents({ events: [new EventUse({ id: 'payment-received', eventDefinitionId: 'PaymentReceivedEvent', direction: EventDirection.CATCH, dependsOn: ['deleted-task'] })] }),
      );

      expect(nodeOf(graph, elementNodeId('task', 'deleted-task'))?.groupId).toBe(laneNodeId(''));
      expect(nodeOf(graph, PAYMENT_RECEIVED)?.groupId).toBe(laneNodeId(''));
    });

    describe('lanes', () => {
      it('puts an event in the lane of its first dependency', () => {
        const graph = convert(withEvents());

        expect(nodeOf(graph, PAYMENT_RECEIVED)?.groupId).toBe(laneNodeId('clerk'));
        expect(nodeOf(graph, ORDER_SHIPPED)?.groupId).toBe(laneNodeId('clerk'));
      });

      it('puts an event depending on nothing in the lane of its first dependent', () => {
        const graph = convert(
          withEvents({ events: [new EventUse({ id: 'payment-received', eventDefinitionId: 'PaymentReceivedEvent', direction: EventDirection.CATCH })] }),
        );

        expect(nodeOf(graph, PAYMENT_RECEIVED)?.groupId).toBe(laneNodeId('manager'));
      });

      it('lends an event the lane of the event it depends on', () => {
        const graph = convert(
          withEvents({
            events: [
              new EventUse({ id: 'payment-received', eventDefinitionId: 'PaymentReceivedEvent', direction: EventDirection.CATCH }),
              new EventUse({ id: 'payment-booked', eventDefinitionId: 'PaymentBooked', direction: EventDirection.THROW, dependsOn: ['payment-received'] }),
            ],
          }),
        );

        expect(nodeOf(graph, elementNodeId('event', 'payment-booked'))?.groupId).toBe(laneNodeId('manager'));
      });

      it('falls back to the first task’s lane for an event joined to no task, even in a cycle of events', () => {
        const [, approve] = workflow().tasks;
        const graph = convert(
          workflow({
            tasks: [approve],
            events: [
              new EventUse({ id: 'ping', eventDefinitionId: 'Ping', direction: EventDirection.THROW, dependsOn: ['pong'] }),
              new EventUse({ id: 'pong', eventDefinitionId: 'Pong', direction: EventDirection.CATCH, dependsOn: ['ping'] }),
            ],
          }),
        );

        expect(nodeOf(graph, elementNodeId('event', 'ping'))?.groupId).toBe(laneNodeId('manager'));
        expect(nodeOf(graph, elementNodeId('event', 'pong'))?.groupId).toBe(laneNodeId('manager'));
      });

      it('puts no event in a lane when lanes are off', () => {
        const graph = convert(withEvents(), { lanes: false });

        expect(graph.nodes.filter((node) => node.data.kind === 'event').every((node) => node.groupId === undefined)).toBe(true);
      });
    });

    it('states the event box rather than letting it be measured', () => {
      const events = convert(withEvents()).nodes.filter((node) => node.data.kind === 'event');

      expect(events).toHaveLength(2);
      events.forEach((node) => {
        expect(node.size).toEqual(EVENT_NODE_SIZE);
        expect(node.autoSize).toBe(false);
      });
    });
  });

  describe('boundary events', () => {
    const OVERDUE = elementNodeId('event', 'review-overdue');
    const ESCALATE = elementNodeId('task', 'escalate-review');

    /** The seeded workflow, its review carrying a two-hour timer that hands the order to an escalation. */
    function withOverdueReview(overdue: Partial<EventUse> = {}, overrides: Partial<Workflow> = {}): Workflow {
      return workflow({
        tasks: [...workflow().tasks, assignment({ taskDefinitionId: 'escalate-review', performedBy: 'manager', dependsOn: ['review-overdue'] })],
        events: [
          new EventUse({
            id: 'review-overdue',
            name: 'Review overdue',
            direction: EventDirection.CATCH,
            timer: { type: TimerType.DURATION, expression: 'PT2H' },
            attachedTo: 'review-order',
            ...overdue,
          }),
        ],
        ...overrides,
      });
    }

    it('draws the event as the smaller circle, naming its task and resolving `interrupting`', () => {
      const node = nodeOf(convert(withOverdueReview()), OVERDUE);

      expect(node?.size).toEqual(BOUNDARY_EVENT_NODE_SIZE);
      expect(node?.autoSize).toBe(false);
      expect(node?.data).toEqual({
        kind: 'event',
        elementId: 'review-overdue',
        label: 'Review overdue',
        description: 'DURATION PT2H',
        direction: EventDirection.CATCH,
        timer: true,
        attachedTo: REVIEW,
        interrupting: true,
      });
    });

    it('marks a non-interrupting event as such', () => {
      expect(nodeOf(convert(withOverdueReview({ interrupting: false })), OVERDUE)?.data.interrupting).toBe(false);
    });

    // Above its siblings, so the half overlapping the card is drawn over the card.
    it('orders the event above the task it sits on', () => {
      expect(nodeOf(convert(withOverdueReview()), OVERDUE)?.zOrder).toBe(1);
      expect(nodeOf(convert(withOverdueReview()), REVIEW)?.zOrder).toBeUndefined();
    });

    // Through `timerOf`: what the form edits is the flattened pair, and an unsaved edit already shows.
    it('draws a timer typed into the flattened fields as a timer', () => {
      const node = nodeOf(convert(withOverdueReview({ timer: undefined, timerType: TimerType.DATE, timerExpression: '2026-10-10T08:00:00Z' })), OVERDUE);

      expect(node?.data.timer).toBe(true);
      expect(node?.data.description).toBe('DATE 2026-10-10T08:00:00Z');
    });

    it('draws a boundary event on a catalog event as a catch, even before it is given a direction', () => {
      const node = nodeOf(convert(withOverdueReview({ timer: undefined, eventDefinitionId: 'OrderCancelledEvent', direction: undefined })), OVERDUE);

      expect(node?.data.timer).toBeUndefined();
      expect(node?.data.direction).toBe(EventDirection.CATCH);
      expect(node?.data.description).toBe('OrderCancelledEvent');
    });

    it('marks an intermediate timer as a timer, with no host', () => {
      const node = nodeOf(convert(withOverdueReview({ attachedTo: undefined, dependsOn: ['review-order'] })), OVERDUE);

      expect(node?.data.timer).toBe(true);
      expect(node?.data.attachedTo).toBeUndefined();
      expect(node?.size).toEqual(EVENT_NODE_SIZE);
    });

    it('draws no edge into the event, even from a dependsOn a form still holds', () => {
      const graph = convert(withOverdueReview({ dependsOn: ['approve-shipment'] }));

      expect(graph.edges.filter((edge) => edge.target === OVERDUE)).toEqual([]);
    });

    it('runs a sequence edge from the event to what depends on it, leaving the circle downwards', () => {
      const out = convert(withOverdueReview()).edges.filter((edge) => edge.source === OVERDUE);

      expect(out.map((edge) => [edge.target, edge.data?.relation, edge.sourcePort, edge.targetPort])).toEqual([[ESCALATE, 'sequence', 'port-bottom', 'port-left']]);
    });

    // Reached by its task being active, so neither a root the start feeds nor a sink feeding the end.
    it('is neither fed by the start nor feeds the end', () => {
      const graph = convert(withOverdueReview({}, { tasks: workflow().tasks }));

      expect(graph.edges.filter((edge) => edge.source === ORDER_DRAFTED).map((edge) => edge.target)).toEqual([REVIEW]);
      expect(graph.edges.filter((edge) => edge.target === END).map((edge) => edge.source)).toEqual([CONFIRM]);
    });

    it('draws the event in its task’s lane', () => {
      expect(nodeOf(convert(withOverdueReview()), OVERDUE)?.groupId).toBe(laneNodeId('clerk'));
    });

    it('lends its task’s lane to an event depending on it', () => {
      const graph = convert(
        withOverdueReview(
          {},
          {
            events: [
              ...withOverdueReview().events,
              new EventUse({ id: 'reminder-sent', eventDefinitionId: 'ReminderSent', direction: EventDirection.THROW, dependsOn: ['review-overdue'] }),
            ],
          },
        ),
      );

      expect(nodeOf(graph, elementNodeId('event', 'reminder-sent'))?.groupId).toBe(laneNodeId('clerk'));
    });

    it('draws a host the workflow does not have as a dangling task in the unassigned lane, the event on it', () => {
      const graph = convert(withOverdueReview({ attachedTo: 'deleted-task' }));

      expect(nodeOf(graph, elementNodeId('task', 'deleted-task'))?.data.unresolved).toBe(true);
      expect(nodeOf(graph, OVERDUE)?.groupId).toBe(laneNodeId(''));
      expect(nodeOf(graph, OVERDUE)?.data.attachedTo).toBe(elementNodeId('task', 'deleted-task'));
    });

    it('treats a blank attachedTo as no host', () => {
      expect(nodeOf(convert(withOverdueReview({ attachedTo: '  ', dependsOn: ['review-order'] })), OVERDUE)?.data.attachedTo).toBeUndefined();
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
      expect(taskSequence(graph)).toHaveLength(2);
    });
  });

  describe('the data layer', () => {
    it('draws an artifact a task reads as an edge into the task', () => {
      const graph = convert(stateless());

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

    // An object of the workflow, not the artifact class: UML's `object : Class`, anonymous without a name.
    it('labels an artifact as the object flowing through the workflow', () => {
      const graph = convert(stateless());

      expect(graph.nodes.find((node) => node.id === ORDER)?.data).toMatchObject({ elementId: 'order-entity', label: 'new_order : Order' });
      expect(graph.nodes.find((node) => node.id === INVOICE)?.data.label).toBe(':Fulfillment Invoice');
    });

    // The artifact feeds the event it starts, not the root after it.
    it('feeds a start event the artifacts it waits for', () => {
      const start = edgesOfRelation(convert(), 'start');

      expect(start.map((edge) => edge.id)).toEqual([elementEdgeId(DRAFT_ORDER, ORDER_DRAFTED)]);
      expect(start[0].data?.label).toBeUndefined();
      expect(start[0].sourcePort).toBe('port-top');
      expect(start[0].targetPort).toBe('port-bottom');
    });

    // The object the event waits for is the object the first task reads when both name the same state, so
    // they meet in one node rather than drawing the order in DRAFT twice.
    it('merges the start event’s required object with the first task’s input in the same state', () => {
      const graph = convert();
      const drafted = graph.nodes.filter((node) => node.id === DRAFT_ORDER);

      expect(drafted).toHaveLength(1);
      expect(drafted[0].data).toMatchObject({ kind: 'artifact', elementId: 'order-entity', label: 'new_order : Order [DRAFT]' });
      expect(edgesOfRelation(graph, 'start').map((edge) => edge.id)).toEqual([elementEdgeId(DRAFT_ORDER, ORDER_DRAFTED)]);
      expect(edgesOfRelation(graph, 'input').map((edge) => edge.id)).toContain(elementEdgeId(DRAFT_ORDER, REVIEW));
    });

    it('keeps the required object apart from a first task that reads another state', () => {
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], artifactStates: [orderState('PENDING', 'CONFIRMED')] })] }));

      expect(nodeIds(graph)).toEqual(expect.arrayContaining([DRAFT_ORDER, elementNodeId('artifact', 'order-entity[PENDING]')]));
      expect(edgesOfRelation(graph, 'input').map((edge) => edge.id)).toEqual([elementEdgeId(elementNodeId('artifact', 'order-entity[PENDING]'), REVIEW)]);
    });

    // UML's rule: the same class may appear several times, but each time in a different state. One task's
    // output state is the next one's input state, so they meet and the lifecycle reads along the flow.
    it('draws one object per state, chaining each task’s output into the next task’s input', () => {
      const graph = convert();

      expect(graph.nodes.filter((node) => node.data.elementId === 'order-entity').map((node) => node.id)).toEqual([DRAFT_ORDER, CONFIRMED_ORDER, SHIPPED_ORDER, DELIVERED_ORDER]);
      expect(edgesOfRelation(graph, 'input').map((edge) => edge.id)).toEqual([elementEdgeId(DRAFT_ORDER, REVIEW), elementEdgeId(CONFIRMED_ORDER, APPROVE), elementEdgeId(SHIPPED_ORDER, CONFIRM)]);
      expect(edgesOfRelation(graph, 'output').map((edge) => edge.id)).toEqual([
        elementEdgeId(REVIEW, CONFIRMED_ORDER),
        elementEdgeId(APPROVE, SHIPPED_ORDER),
        elementEdgeId(CONFIRM, DELIVERED_ORDER),
        elementEdgeId(CONFIRM, INVOICE),
      ]);
      expect(graph.nodes.find((node) => node.id === SHIPPED_ORDER)?.data.label).toBe('new_order : Order [SHIPPED]');
    });

    // No row for an artifact, or a row with that side blank, is a task saying nothing about the state.
    it('falls back to the stateless object where no state is stated', () => {
      const graph = convert();

      expect(nodeIds(graph)).toContain(INVOICE);
      expect(nodeIds(graph)).not.toContain(ORDER);

      const blankOutput = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], artifactStates: [orderState('DRAFT', ' ')] })] }));
      expect(edgesOfRelation(blankOutput, 'output').map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, ORDER)]);
    });

    it('draws each artifact once when no task states any state', () => {
      const graph = convert(stateless({ startEvents: [] }));

      expect(graph.nodes.filter((node) => node.data.kind === 'artifact').map((node) => node.id)).toEqual([ORDER, INVOICE]);
    });

    // A FOREIGN_KEY control may write the picked artifact itself into the row rather than its id.
    it('reads an artifact state whose artifact the reference control wrote as a whole entity', () => {
      const state = orderState('DRAFT', 'CONFIRMED');
      (state as unknown as Record<string, unknown>)['artifactDefinitionId'] = { id: 'order-entity', name: 'Order' };
      const graph = convert(workflow({ tasks: [assignment({ taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], artifactStates: [state] })] }));

      expect(edgesOfRelation(graph, 'output').map((edge) => edge.id)).toEqual([elementEdgeId(REVIEW, CONFIRMED_ORDER)]);
    });

    it('feeds a start event the stateless object when no state is required', () => {
      const anyState = workflow({
        startEvents: [
          new StartEvent({
            id: 'order-drafted',
            startType: WorkflowStartConditionType.INPUT_ARTIFACT,
            requiredArtifacts: [new RequiredStartArtifact({ artifactDefinitionId: 'order-entity' })],
          }),
        ],
      });

      expect(edgesOfRelation(convert(anyState), 'start').map((edge) => edge.id)).toEqual([elementEdgeId(ORDER, ORDER_DRAFTED)]);
    });

    it('draws no start artifact for a workflow without start events', () => {
      expect(edgesOfRelation(convert(workflow({ startEvents: [] })), 'start')).toEqual([]);
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
      expect(taskSequence(graph).map((edge) => edge.id)).toEqual([elementEdgeId(elementNodeId('task', 'nothing-of-the-kind'), REVIEW)]);
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
