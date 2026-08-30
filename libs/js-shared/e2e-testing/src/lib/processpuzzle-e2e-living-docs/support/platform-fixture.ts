/**
 * STUB — illustrative only.
 *
 * In the real suite this file is replaced by the platform fixture exported
 * from @processpuzzle/e2e-testing, which talks to the running base-entity /
 * base-rule / base-state / base-workflow backends (or their test
 * containers) over the real base-*-api.yaml contracts.
 *
 * This in-memory fake exists only so the manifest + spec + doc generator in
 * this package are runnable end to end without a live backend, to
 * demonstrate the wiring. Swap the `platform` fixture implementation below
 * for the real one and nothing else in this package needs to change.
 */
import { test as base } from '@playwright/test';

export interface PlatformContext {
  entities: {
    define(def: unknown): Promise<void>;
    create(entityKey: string, payload: Record<string, unknown>): Promise<{ id: string }>;
    createExpectingViolation(entityKey: string, payload: Record<string, unknown>, ruleKey: string): Promise<void>;
    update(entityKey: string, id: string, patch: Record<string, unknown>): Promise<void>;
  };
  rules: {
    define(def: unknown): Promise<void>;
  };
  states: {
    define(def: unknown): Promise<void>;
    trigger(entityKey: string, id: string, triggerKey: string): Promise<void>;
    triggerExpectingViolation(entityKey: string, id: string, triggerKey: string, ruleKey: string): Promise<void>;
    expectState(entityKey: string, id: string, expectedState: string): Promise<void>;
  };
  workflows: {
    define(def: unknown): Promise<void>;
    expectTaskInstance(taskUseKey: string, expected: Record<string, unknown>): Promise<void>;
    completeTask(taskUseKey: string): Promise<void>;
  };
}

function fail(message: string): never {
  throw new Error(message);
}

function makeInMemoryPlatform(): PlatformContext {
  const entities = new Map<string, Record<string, unknown>>();
  const states = new Map<string, string>();
  let counter = 0;
  const nextId = () => `id-${++counter}`;

  return {
    entities: {
      async define() {
        // no-op stub: real impl POSTs to base-entity-api.yaml
      },
      async create(entityKey, payload) {
        if (entityKey === 'order' && Number(payload.total) <= 0) {
          fail('order-total-positive violated');
        }
        const id = nextId();
        entities.set(id, { entityKey, ...payload });
        states.set(id, 'DRAFT');
        return { id };
      },
      async createExpectingViolation(entityKey, payload, ruleKey) {
        try {
          await this.create(entityKey, payload);
          fail(`Expected rule violation "${ruleKey}" but the entity was created without one`);
        } catch {
          // expected — the rule fired
        }
      },
      async update(_entityKey, id, patch) {
        const existing = entities.get(id) ?? fail(`Unknown entity id ${id}`);
        entities.set(id, { ...existing, ...patch });
      },
    },
    rules: {
      async define() {
        // no-op stub: real impl POSTs PPCL rules to base-rule-backend
      },
    },
    states: {
      async define() {
        // no-op stub: real impl POSTs the state machine to base-state-api.yaml
      },
      async trigger(_entityKey, id, triggerKey) {
        const transitions: Record<string, string> = {
          submit: 'SUBMITTED',
          startReview: 'UNDER_REVIEW',
          approve: 'APPROVED',
          reject: 'REJECTED',
          fulfill: 'FULFILLED',
        };
        const next = transitions[triggerKey] ?? fail(`Unknown triggerKey "${triggerKey}"`);
        states.set(id, next);
      },
      async triggerExpectingViolation(_entityKey, id, triggerKey, ruleKey) {
        const current = states.get(id);
        // Stub simulates the cross-module rule check that in the real
        // platform runs via the @Validate AOP aspect before the transition.
        if (triggerKey === 'approve' && current === 'UNDER_REVIEW') {
          return; // "blocked" — document not yet signed in this stub's model
        }
        fail(`Expected rule violation "${ruleKey}" on trigger "${triggerKey}"`);
      },
      async expectState(_entityKey, id, expectedState) {
        const actual = states.get(id);
        if (actual !== expectedState) {
          fail(`Expected state "${expectedState}", got "${actual}"`);
        }
      },
    },
    workflows: {
      async define() {
        // no-op stub: real impl POSTs the SPEM workflow to base-workflow-api.yaml
      },
      async expectTaskInstance() {
        // no-op stub: real impl queries the ProcessInstance/TaskInstance
      },
      async completeTask(_taskUseKey) {
        // no-op stub: real impl completes the task, which fires the SPEM
        // state-machine event that the real states.trigger('approve') would
      },
    },
  };
}

export const test = base.extend<{ platform: PlatformContext }>({
  // eslint-disable-next-line no-empty-pattern
  platform: async ({}, use) => {
    await use(makeInMemoryPlatform());
  },
});
