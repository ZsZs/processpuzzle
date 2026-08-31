import { loadMetadataFixtureRecord } from '../support/metadata-fixture-loader';
import type { PlatformContext } from '../support/platform-fixture';

export interface ScenarioState {
  orderId?: string;
  docId?: string;
}

export interface ScenarioStep {
  /** Doubles as the Playwright test.step() title AND the living-doc heading — write it as a sentence. */
  title: string;
  kind: 'provision' | 'behavior';
  /** Fixture files (relative to /fixtures) this step provisions or exercises. The doc generator inlines their raw YAML under this step's heading. */
  fixtures?: string[];
  run(ctx: PlatformContext, state: ScenarioState): Promise<void>;
}

const fx = (relativePath: string) => loadMetadataFixtureRecord(relativePath).data;

export const orderLifecycleManifest: ScenarioStep[] = [
  {
    title: 'Provision: Order & OrderDocument entity definitions',
    kind: 'provision',
    fixtures: ['order-scenario/entity-definition.yaml', 'order-scenario/document-definition.yaml'],
    async run(ctx) {
      await ctx.entities.define(fx('order-scenario/entity-definition.yaml'));
      await ctx.entities.define(fx('order-scenario/document-definition.yaml'));
    },
  },
  {
    title: 'Provision: PPCL rules (total > 0, signed document required to approve)',
    kind: 'provision',
    fixtures: ['order-scenario/rules.yaml'],
    async run(ctx) {
      await ctx.rules.define(fx('order-scenario/rules.yaml'));
    },
  },
  {
    title: 'Provision: Order lifecycle state machine',
    kind: 'provision',
    fixtures: ['order-scenario/state-machine.yaml'],
    async run(ctx) {
      await ctx.states.define(fx('order-scenario/state-machine.yaml'));
    },
  },
  {
    title: 'Provision: Order review workflow (SPEM)',
    kind: 'provision',
    fixtures: ['order-scenario/workflow.yaml'],
    async run(ctx) {
      await ctx.workflows.define(fx('order-scenario/workflow.yaml'));
    },
  },
  {
    title: 'Create an Order with total = 0 — rejected by rule order-total-positive',
    kind: 'behavior',
    async run(ctx) {
      await ctx.entities.createExpectingViolation('order', { customerId: 'C-1', total: 0 }, 'order-total-positive');
    },
  },
  {
    title: 'Create a valid Order and submit it (triggerKey: submit)',
    kind: 'behavior',
    async run(ctx, state) {
      const { id } = await ctx.entities.create('order', { customerId: 'C-1', total: 120.0 });
      state.orderId = id;
      await ctx.states.trigger('order', id, 'submit');
    },
  },
  {
    title: 'Start review — a workflow task instance is created',
    kind: 'behavior',
    async run(ctx, state) {
      await ctx.states.trigger('order', state.orderId!, 'startReview');
      await ctx.workflows.expectTaskInstance('reviewOrderUse', { status: 'ACTIVE' });
    },
  },
  {
    title: 'Attempt to approve without a signed document — blocked by rule order-requires-signed-document',
    kind: 'behavior',
    async run(ctx, state) {
      await ctx.states.triggerExpectingViolation('order', state.orderId!, 'approve', 'order-requires-signed-document');
    },
  },
  {
    title: 'Sign the document and complete the review task — SPEM fires the approve transition',
    kind: 'behavior',
    async run(ctx, state) {
      await ctx.entities.update('orderDocument', state.docId ?? 'doc-1', { signed: true });
      await ctx.workflows.completeTask('reviewOrderUse');
      await ctx.states.expectState('order', state.orderId!, 'APPROVED');
    },
  },
  {
    title: 'Fulfill the order',
    kind: 'behavior',
    async run(ctx, state) {
      await ctx.states.trigger('order', state.orderId!, 'fulfill');
      await ctx.states.expectState('order', state.orderId!, 'FULFILLED');
    },
  },
];
