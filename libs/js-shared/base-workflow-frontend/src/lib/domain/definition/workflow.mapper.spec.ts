import { describe, expect, it } from 'vitest';
import { ArtifactUse, JoinType, RoleUse, ToolUse, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from './workflow';
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
      expect(workflow.artifacts).toEqual([{ artifactDefinitionId: 'order-entity' }, { artifactDefinitionId: 'fulfillment-invoice' }]);
      expect(workflow.artifacts[0]).toBeInstanceOf(ArtifactUse);
      expect(workflow.tools).toEqual([{ toolDefinitionId: 'automated-check-tool' }]);
      expect(workflow.tools[0]).toBeInstanceOf(ToolUse);
    });

    // Lifted out of the nested `startCondition` onto the entity, the way `ToolDefinitionMapper` lifts
    // `auth`, so the generic form can build one control per field.
    it('lifts the start condition onto the entity', () => {
      expect(workflow.startType).toBe(WorkflowStartConditionType.INPUT_ARTIFACT);
      expect(workflow.requiredArtifacts).toEqual([{ artifactDefinitionId: 'order-entity', state: 'DRAFT' }]);
    });

    it('maps every assignment rather than passing the list through', () => {
      expect(workflow.tasks).toHaveLength(3);
      expect(workflow.tasks[0]).toBeInstanceOf(WorkflowTaskAssignment);
      expect(workflow.tasks[0].taskDefinitionId).toBe('review-order');
      expect(workflow.tasks[0].performedBy).toBe('clerk');
      expect(workflow.tasks[1].dependsOn).toEqual(['review-order']);
      expect(workflow.tasks[1].joinType).toBe(JoinType.ALL);
    });

    // The one reference list of this entity that really is an id array by contract, so the one the
    // `RELATED_ENTITIES` flattening still applies to. Applied on the way *in* as well as out, so a
    // payload holding embedded roles loads as ids rather than half-flattening on the next save.
    it('flattens authorizedRoles that arrived as whole roles', () => {
      const embedded = mapper.fromDto({
        id: 'p1',
        startCondition: { startType: 'ROLE_DEFINITION', authorizedRoles: [{ id: 'clerk', name: 'Order Clerk' }, 'manager'] },
      });

      expect(embedded.authorizedRoles).toEqual(['clerk', 'manager']);
    });

    it('defaults a list the document omits, so no control gets undefined', () => {
      const bare = mapper.fromDto({ id: 'p1', name: 'P1' });

      expect(bare.roles).toEqual([]);
      expect(bare.artifacts).toEqual([]);
      expect(bare.tools).toEqual([]);
      expect(bare.tasks).toEqual([]);
      expect(bare.requiredArtifacts).toEqual([]);
      expect(bare.authorizedRoles).toEqual([]);
    });

    it('leaves the start condition unset for a workflow that declares none', () => {
      expect(mapper.fromDto({ id: 'p1' }).startType).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded workflow without losing a reference or an assignment', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(dto.id).toBe('order-fulfillment-workflow');
      expect(dto.roles).toEqual([{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }]);
      expect(dto.artifacts).toEqual([{ artifactDefinitionId: 'order-entity' }, { artifactDefinitionId: 'fulfillment-invoice' }]);
      expect(dto.tools).toEqual([{ toolDefinitionId: 'automated-check-tool' }]);
      expect(dto.tasks).toHaveLength(3);
      expect(dto.tasks?.[2]).toMatchObject({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk' });
    });

    // The whole point of modelling the start condition: the PUT is a full replacement, so a field the
    // mapper does not carry is a field the save deletes. Saving a seeded workflow unchanged used to drop
    // its INPUT_ARTIFACT condition silently.
    it('re-nests the start condition it lifted, required artifacts included', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(dto.startCondition).toEqual({
        startType: WorkflowStartConditionType.INPUT_ARTIFACT,
        requiredArtifacts: [{ artifactDefinitionId: 'order-entity', state: 'DRAFT' }],
        eventType: undefined,
        payloadMapping: undefined,
        authorizedRoles: [],
        milestoneRef: undefined,
        preconditionExpression: undefined,
      });
    });

    // `startType` is the object's only required field by contract, so it is what decides whether there is
    // an object at all: a workflow started only through /instances is a different statement from one
    // whose condition is present but blank.
    it('omits the start condition entirely when no start type was chosen', () => {
      expect(mapper.toDto(new Workflow({ id: 'p1' })).startCondition).toBeUndefined();
    });

    // The save path of the `RELATED_ENTITIES` control over the authorized roles: the user picked one from
    // its own list, so the control put the whole record in the form value, and the contract wants the id.
    it('flattens the roles the reference control wrote on selection', () => {
      const workflow = new Workflow({ id: 'p1', startType: WorkflowStartConditionType.ROLE_DEFINITION });
      (workflow as unknown as Record<string, unknown>)['authorizedRoles'] = ['clerk', { id: 'manager', name: 'Order Manager' }];

      expect(mapper.toDto(workflow).startCondition?.authorizedRoles).toEqual(['clerk', 'manager']);
    });

    // `PUT /workflows/{workflowId}` is a full replacement, so an absent list is an emptied one. A blank
    // workflow therefore has to send four empty arrays rather than four missing keys.
    it('emits every list unconditionally', () => {
      const dto = mapper.toDto(new Workflow({ id: 'p1', name: 'P1' }));

      expect(dto.roles).toEqual([]);
      expect(dto.artifacts).toEqual([]);
      expect(dto.tools).toEqual([]);
      expect(dto.tasks).toEqual([]);
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

      expect(Object.keys(dto).sort()).toEqual([
        'artifacts',
        'createdAt',
        'description',
        'extends',
        'id',
        'name',
        'roles',
        'startCondition',
        'tasks',
        'tools',
        'updatedAt',
        'version',
      ]);
    });
  });
});
