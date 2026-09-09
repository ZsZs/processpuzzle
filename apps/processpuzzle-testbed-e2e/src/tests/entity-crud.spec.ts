import { defineEntityCrudSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityCrudSuite({
  registryPath: REGISTRY_PATH,
  routePrefix: testConfig.routePrefix,
  excludedEntities: [
    {
      entityName: 'State Machine Definition',
      // Four cross-field invariants stand between the generated fixture and a saved machine, and no generic
      // value satisfies any of them. The POST comes back 400 request.validation-failed on the first:
      //   - `states` is `minItems: 1` in base-state-api.yaml, and this suite never fills an embedded list —
      //     that is the relationship suite's job, and it needs a saved owner to start from.
      //   - `entityName` has to name an entity type base-entity manages, `stateAttributeKey` a TEXT or ENUM
      //     attribute *of that type*, and `initialStateKey` one of the state keys declared above — all three
      //     checked by StateMachineTopologyValidator, all three filled here with generated prose.
      // Authoring a machine is covered by base-state-frontend's own unit tests and by the seeded machines in
      // processpuzzle-testbed-state-machines.yaml. Drop this entry if the fixture factory ever learns to seed
      // an entity from application-supplied values.
      reason: 'a state machine needs at least one state and an entityName/stateAttributeKey pair naming a real base-entity type, none of which generated fixture data can produce',
    },
    {
      entityName: 'Workflow',
      // `tasks` is `required` in WorkflowInput, and this suite never fills an embedded list — that
      // is the relationship suite's job, and it needs a saved owner to start from. The POST comes back
      // 400 request.validation-failed before anything else in the workflow can be exercised. Authoring a
      // workflow is covered by base-workflow-frontend's own unit tests and by the seeded workflow in
      // processpuzzle-testbed-workflows.yaml.
      reason: 'a workflow requires at least one task, and this suite cannot fill an embedded list',
    },
    {
      entityName: 'Task Definition',
      // The same shape of exclusion as the workflow above, one control type over: `performedByRoles` is
      // `minItems: 1` in TaskDefinitionInput, and it is a RELATED_ENTITIES control — which this suite
      // deliberately never fills, a relationship not being a scalar. So Save stays disabled on a pristine
      // invalid form and the POST is never sent. Authoring a task is covered by base-workflow-frontend's own
      // unit tests, by the seeded tasks in processpuzzle-testbed-workflows.yaml, and end-to-end by the
      // [Workflow Role Definition] relationship cases, which exercise the same control against a creatable
      // owner. Drop this entry if the fixture factory ever learns to attach a reference.
      reason: 'a task requires at least one performing role, and this suite cannot fill a relationship control',
    },
    {
      entityName: 'Workflow Instance',
      // Not a fixture problem: the runtime side of base-workflow-api.yaml has no PUT at all. An instance is
      // started by POST /instances with a StartWorkflowRequest — a different schema from the instance it
      // returns — cancelled by DELETE, and never edited. The descriptor says so with `isAbstract`, which
      // disables New, Edit, Delete and Save, so there is nothing here for a CRUD suite to drive.
      reason: 'workflow instances are read-only by contract; the descriptor is isAbstract, so New/Edit/Delete/Save are all disabled',
    },
  ],
});
