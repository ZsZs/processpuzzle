import { describe, expect, it } from 'vitest';
import { ArtifactUse, JoinType, RequiredStartArtifact, RoleUse, StartEvent, TaskArtifactState, ToolUse, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from './workflow';
import { WorkflowMapper } from './workflow.mapper';
import { WORKFLOW_DTO } from './test-workflow';

describe('WorkflowMapper', () => {
  const mapper = new WorkflowMapper();

  describe('fromDto', () => {
    const workflow = mapper.fromDto(WORKFLOW_DTO);

    it('reads the header fields of the seeded workflow', () => {
      expect(workflow.id).toBe('order-fulfillment-workflow');
      expect(workflow.name).toBe('Order Fulfillment Workflow');
      expect(workflow.version).toBe(3);
      expect(workflow.activeInstances).toBe(1);
    });

    // The regression this whole revision exists for: `roles`, `artifacts` and `tools` are arrays of
    // `*Use` objects wrapping a definition id, and the mapper read them as id arrays through
    // `toReferenceIds`, which looks for `.id` — so every one of the three loaded empty against a real
    // backend while the fixture's bare id arrays kept the specs green.
    it('reads the three catalog references as Use rows', () => {
      expect(workflow.roles).toEqual([{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }]);
      expect(workflow.roles[0]).toBeInstanceOf(RoleUse);
      expect(workflow.artifacts).toEqual([{ artifactDefinitionId: 'order-entity', objectName: 'new_order' }, { artifactDefinitionId: 'fulfillment-invoice' }]);
      expect(workflow.artifacts[0]).toBeInstanceOf(ArtifactUse);
      expect(workflow.tools).toEqual([{ toolDefinitionId: 'automated-check-tool' }]);
      expect(workflow.tools[0]).toBeInstanceOf(ToolUse);
    });

    // Mapped row by row, so every field lands on the control that edits it.
    it('maps every start event, with the catalog event it waits for and its payload mapping', () => {
      expect(workflow.startEvents).toHaveLength(1);
      expect(workflow.startEvents[0]).toBeInstanceOf(StartEvent);
      expect(workflow.startEvents[0]).toMatchObject({ id: 'order-created', name: 'OrderCreatedEvent', startType: WorkflowStartConditionType.TRIGGERING_EVENT, eventType: 'OrderCreatedEvent' });
      expect(workflow.startEvents[0].payloadMapping).toEqual({ orderId: '$.subjectId' });
      expect(workflow.startEvents[0].requiredArtifacts).toEqual([]);
    });

    it('maps the artifacts an INPUT_ARTIFACT start event waits for', () => {
      const event = mapper.fromDto({ id: 'p1', startEvents: [{ id: 'order-drafted', startType: 'INPUT_ARTIFACT', requiredArtifacts: [{ artifactDefinitionId: 'order-entity', state: 'DRAFT' }] }] }).startEvents[0];

      expect(event.requiredArtifacts).toEqual([{ artifactDefinitionId: 'order-entity', state: 'DRAFT' }]);
      expect(event.requiredArtifacts[0]).toBeInstanceOf(RequiredStartArtifact);
    });

    it('maps every assignment rather than passing the list through', () => {
      expect(workflow.tasks).toHaveLength(3);
      expect(workflow.tasks[0]).toBeInstanceOf(WorkflowTaskAssignment);
      expect(workflow.tasks[0].taskDefinitionId).toBe('review-order');
      expect(workflow.tasks[0].performedBy).toBe('clerk');
      expect(workflow.tasks[1].dependsOn).toEqual(['review-order']);
      expect(workflow.tasks[1].joinType).toBe(JoinType.ALL);
    });

    // Row by row, so the states land on the controls that edit them - and as entities an `Add` can extend.
    it('maps the artifact states of every assignment', () => {
      expect(workflow.tasks.map((task) => task.artifactStates)).toEqual([
        [{ artifactDefinitionId: 'order-entity', inputState: 'DRAFT', outputState: 'CONFIRMED' }],
        [{ artifactDefinitionId: 'order-entity', inputState: 'CONFIRMED', outputState: 'SHIPPED' }],
        [{ artifactDefinitionId: 'order-entity', inputState: 'SHIPPED', outputState: 'DELIVERED' }],
      ]);
      expect(workflow.tasks[0].artifactStates[0]).toBeInstanceOf(TaskArtifactState);
    });

    // `null` is what the contract's nullable states may arrive as; the model knows only absent.
    it('reads a null state as absent and defaults a missing list', () => {
      const read = mapper.fromDto({
        id: 'p1',
        tasks: [{ taskDefinitionId: 't1', artifactStates: [{ artifactDefinitionId: 'order-entity', inputState: null, outputState: 'CONFIRMED' }] }, { taskDefinitionId: 't2' }],
      });

      expect(read.tasks[0].artifactStates[0].inputState).toBeUndefined();
      expect(read.tasks[0].artifactStates[0].outputState).toBe('CONFIRMED');
      expect(read.tasks[1].artifactStates).toEqual([]);
    });

    // The one reference list of this entity that really is an id array by contract, so the one the
    // `RELATED_ENTITIES` flattening still applies to. Applied on the way *in* as well as out, so a
    // payload holding embedded roles loads as ids rather than half-flattening on the next save.
    it('flattens authorizedRoles that arrived as whole roles', () => {
      const embedded = mapper.fromDto({
        id: 'p1',
        startEvents: [{ id: 'by-hand', startType: 'ROLE_DEFINITION', authorizedRoles: [{ id: 'clerk', name: 'Order Clerk' }, 'manager'] }],
      });

      expect(embedded.startEvents[0].authorizedRoles).toEqual(['clerk', 'manager']);
    });

    it('defaults a list the document omits, so no control gets undefined', () => {
      const bare = mapper.fromDto({ id: 'p1', name: 'P1' });

      expect(bare.roles).toEqual([]);
      expect(bare.artifacts).toEqual([]);
      expect(bare.tools).toEqual([]);
      expect(bare.tasks).toEqual([]);
      expect(bare.startEvents).toEqual([]);
    });

    it('defaults the lists of a start event the document omits', () => {
      const event = mapper.fromDto({ id: 'p1', startEvents: [{ id: 'by-hand', startType: 'ROLE_DEFINITION' }] }).startEvents[0];

      expect(event.requiredArtifacts).toEqual([]);
      expect(event.authorizedRoles).toEqual([]);
      expect(event.name).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded workflow without losing a reference or an assignment', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(dto.id).toBe('order-fulfillment-workflow');
      expect(dto.roles).toEqual([{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }]);
      expect(dto.artifacts).toEqual([{ artifactDefinitionId: 'order-entity', objectName: 'new_order' }, { artifactDefinitionId: 'fulfillment-invoice' }]);
      expect(dto.tools).toEqual([{ toolDefinitionId: 'automated-check-tool' }]);
      expect(dto.tasks).toHaveLength(3);
      expect(dto.tasks?.[2]).toMatchObject({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk' });
    });

    // The PUT is a full replacement, so a field the mapper does not carry is a field the save deletes.
    it('round-trips every start event, payload mapping included', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(dto.startEvents).toHaveLength(1);
      expect(dto.startEvents?.[0]).toEqual({
        id: 'order-created',
        name: 'OrderCreatedEvent',
        startType: WorkflowStartConditionType.TRIGGERING_EVENT,
        requiredArtifacts: [],
        eventType: 'OrderCreatedEvent',
        payloadMapping: { orderId: '$.subjectId' },
        authorizedRoles: [],
        milestoneRef: undefined,
        preconditionExpression: undefined,
      });
    });

    // Every field of the row travels whatever the type, so switching the type and back loses nothing.
    it('writes the fields the start type does not read as well', () => {
      const event = new StartEvent({ id: 'e1', startType: WorkflowStartConditionType.ROLE_DEFINITION, eventType: 'order.submitted', milestoneRef: 'm1', preconditionExpression: 'x' });

      expect(mapper.toDto(new Workflow({ id: 'p1', startEvents: [event] })).startEvents?.[0]).toMatchObject({
        eventType: 'order.submitted',
        milestoneRef: 'm1',
        preconditionExpression: 'x',
      });
    });

    // The save path of the `RELATED_ENTITIES` control over the authorized roles: the user picked one from
    // its own list, so the control put the whole record in the form value, and the contract wants the id.
    it('flattens the roles the reference control wrote on selection', () => {
      const event = new StartEvent({ id: 'by-hand', startType: WorkflowStartConditionType.ROLE_DEFINITION });
      (event as unknown as Record<string, unknown>)['authorizedRoles'] = ['clerk', { id: 'manager', name: 'Order Manager' }];

      expect(mapper.toDto(new Workflow({ id: 'p1', startEvents: [event] })).startEvents?.[0].authorizedRoles).toEqual(['clerk', 'manager']);
    });

    // `PUT /workflows/{workflowId}` is a full replacement, so an absent list is an emptied one. A blank
    // workflow therefore has to send five empty arrays rather than five missing keys.
    it('emits every list unconditionally', () => {
      const dto = mapper.toDto(new Workflow({ id: 'p1', name: 'P1' }));

      expect(dto.roles).toEqual([]);
      expect(dto.artifacts).toEqual([]);
      expect(dto.tools).toEqual([]);
      expect(dto.tasks).toEqual([]);
      expect(dto.startEvents).toEqual([]);
    });

    it('round-trips the artifact states of every assignment', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(dto.tasks?.[1].artifactStates).toEqual([{ artifactDefinitionId: 'order-entity', inputState: 'CONFIRMED', outputState: 'SHIPPED' }]);
    });

    // The PUT replaces the whole workflow, so an assignment without states says so with an empty list.
    it('always sends the artifact states, if only as an empty list', () => {
      const dto = mapper.toDto(new Workflow({ tasks: [new WorkflowTaskAssignment({ taskDefinitionId: 't1' })] }));

      expect(dto.tasks?.[0].artifactStates).toEqual([]);
    });

    // A cleared text box writes '' - which the backend would read as a state named nothing.
    it('omits a blank state rather than sending an empty string', () => {
      const states = [new TaskArtifactState({ artifactDefinitionId: 'order-entity', inputState: '', outputState: '  ' })];

      expect(mapper.toDto(new Workflow({ tasks: [new WorkflowTaskAssignment({ taskDefinitionId: 't1', artifactStates: states })] })).tasks?.[0].artifactStates).toEqual([
        { artifactDefinitionId: 'order-entity', inputState: undefined, outputState: undefined },
      ]);
    });

    // The FOREIGN_KEY control may write the picked artifact itself rather than its id.
    it('flattens an artifact the reference control wrote as a whole entity', () => {
      const state = new TaskArtifactState({ inputState: 'DRAFT' });
      (state as unknown as Record<string, unknown>)['artifactDefinitionId'] = { id: 'order-entity', name: 'Order' };

      expect(mapper.toDto(new Workflow({ tasks: [new WorkflowTaskAssignment({ taskDefinitionId: 't1', artifactStates: [state] })] })).tasks?.[0].artifactStates?.[0].artifactDefinitionId).toBe(
        'order-entity',
      );
    });

    it('spells an unticked checkbox as false rather than omitting it', () => {
      const dto = mapper.toDto(new Workflow({ tasks: [new WorkflowTaskAssignment({ taskDefinitionId: 't1' })] }));

      expect(dto.tasks?.[0].parallel).toBe(false);
      expect(dto.tasks?.[0].override).toBe(false);
    });

    // Server-computed and marked read-only in the contract: the backend recounts it per list row, so
    // sending it back would be sending a derived value it is about to overwrite.
    it('never sends activeInstances back', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect('activeInstances' in dto).toBe(false);
    });

    // Listed field by field rather than spread, so a control the form may gain later cannot leak.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(Object.keys(dto).sort()).toEqual(['artifacts', 'createdAt', 'description', 'extends', 'id', 'name', 'roles', 'startEvents', 'tasks', 'tools', 'updatedAt', 'version']);
    });
  });
});
