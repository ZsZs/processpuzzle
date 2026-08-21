/**
 * The DTO shape `GET /organizations/{orgKey}/state-machines` actually returns: a complete machine per
 * entry — states, transitions, guards and actions — the same one `getStateMachineDefinition` serves.
 *
 * It lives here rather than in a single spec so that the service, store and mapper specs cannot drift
 * onto a header-only mock. That shape is precisely what makes an edit form look right and the next
 * full-replacement PUT empty the machine, since `states` and `transitions` are sent unconditionally.
 *
 * Named `test-*` so `tsconfig.lib.json` keeps it out of the published package.
 */
export const STATE_MACHINE_DEFINITION_DTO = {
  entityName: 'order',
  name: 'Order State Machine',
  description: "State machine governing an order's lifecycle.",
  stateAttributeKey: 'status',
  initialStateKey: 'DRAFT',
  states: [
    { key: 'DRAFT', name: 'Draft', description: 'Entered but not reviewed.', terminal: false, locked: false },
    { key: 'DELIVERED', name: 'Delivered', terminal: true, locked: true, metadata: { colour: 'green' } },
  ],
  transitions: [
    {
      key: 'confirm',
      name: 'Confirm',
      sourceStateKey: 'DRAFT',
      targetStateKey: 'DELIVERED',
      triggerKey: 'confirm',
      guards: [{ beanName: 'sufficientBalanceGuard', params: { threshold: '100' } }],
      actions: [{ beanName: 'sendApprovalNotificationAction' }],
    },
  ],
  orgKey: 'processpuzzle-testbed',
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

/**
 * A second machine, so list specs can tell entries apart — and, deliberately, one carrying the
 * `isFinal` / `isLocked` spelling of base-state-backend's `State` record, which is what the seed YAML
 * uses and json-server therefore serves verbatim.
 */
export const OTHER_STATE_MACHINE_DEFINITION_DTO = {
  entityName: 'dynamic-entity',
  name: 'Dynamic Entity State Machine',
  stateAttributeKey: 'enumAttr',
  initialStateKey: 'DRAFT',
  states: [
    { key: 'DRAFT', name: 'Draft', isFinal: false, isLocked: false },
    { key: 'ARCHIVED', name: 'Archived', isFinal: true, isLocked: true },
  ],
  transitions: [{ key: 'archive', name: 'Archive', sourceStateKey: 'DRAFT', targetStateKey: 'ARCHIVED', triggerKey: 'archive' }],
  version: 1,
};

/** Wraps entries in the `PageOf_StateMachineDefinition` envelope the Spring backend answers with. */
export function pageOfStateMachineDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
