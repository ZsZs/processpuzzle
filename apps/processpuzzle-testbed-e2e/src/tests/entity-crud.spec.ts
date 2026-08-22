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
  ],
});
