# @processpuzzle/base-rule

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-rule-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_rule_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_rule_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-rule?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-rule)

## Introduction

`@processpuzzle/base-rule` is the front-end building block of the ProcessPuzzle Rule Engine. It lets end-users author business rules at run-time and lets any [`@processpuzzle/base-entity`](../base-entity-frontend/README.md) form evaluate those rules against its current entity — without recompiling the application. It complements the [`base-rule-backend`](../../java-shared/base-rule-backend/README.md) Spring Boot module that persists the rule definitions and can run the same expressions server-side.

From these **Inputs:**

- **A `BaseRule` record** – a persistable entity holding a boolean expression, its `context` (the entity type it applies to), a severity, and an optional human-readable message.
- **A subject entity** – any object satisfying `BaseEntity`.

the library produces these **Outputs:**

- A **`RuleEvaluationResult`** – `{ passed, severity, message, translocoId, fields, error }` — consumed by `BaseEntityFormComponent` to surface validation feedback in the generated form.
- A ready-made **CRUD UI** for authoring rules (`BaseRuleContainerComponent` + `BASE_RULE_ROUTES`), including a **Dry-run dialog** that evaluates the current rule against a picked entity instance.

The main pieces the library exposes:

- **`BaseRule`** – the rule entity (`id`, `name`, `context`, `expression`, `severity`, `message`, `translocoId`, `extendsRuleId`, `override`, `enabled`, `fields`, …).
- **`Severity`** – `ERROR | WARNING | INFO`.
- **`BaseRuleService` / `BaseRuleMapper` / `BaseRuleStore`** – the standard `BaseEntity*` triplet backing the CRUD UI; the service talks to the `rules` REST resource.
- **`BaseRuleEvaluatorService`** – compiles a rule's `expression` string into a cached `(entity) => boolean` function and returns a `RuleEvaluationResult`. Expressions run in strict mode; the compiled function receives a single `entity` argument.
- **`BaseRuleEngineAdapter`** – implements the `RuleEngine` contract from `@processpuzzle/base-entity`. Loads all enabled rules whose `context` matches a given entity type and evaluates them on demand.
- **`provideBaseRuleEngine()`** – one-line provider that binds `RULE_ENGINE` (declared by base-entity) to the adapter above. Once provided, every `BaseEntityFormComponent` in the route subtree runs the matching rules automatically.
- **`BaseRuleFacade` / `BaseRuleContainerComponent` / `BASE_RULE_ROUTES`** – the drop-in UI for managing rules. The container adds a **Dry run…** button next to the standard form actions.
- **`createBaseRuleDescriptor(getContextOptions)`** – the `BaseEntityDescriptor` driving the rule editor form. Pass a getter that returns the available entity-context names (typically the keys of `BASE_ENTITY_FACADE_REGISTRY`).

## Usage

There are two independent capabilities to enable:

1. **Author rules** – mount the built-in CRUD UI at a route in your application.
2. **Evaluate rules against entities** – provide the rule engine so that any base-entity form runs its rules on submit.

The snippets below are taken from the [Base Rule sample in the testbed](https://github.com/ZsZs/processpuzzle/tree/develop/apps/processpuzzle-testbed/src/app/content/base-rules).

### 1. Enable the rule engine for a route subtree

`provideBaseRuleEngine()` binds `RULE_ENGINE` (the token declared by `@processpuzzle/base-entity`) to `BaseRuleEngineAdapter`. Provide it once at whatever route level should have rule-driven validation.

```typescript
import { provideBaseRuleEngine } from '@processpuzzle/base-rule';

export const APP_ROUTES: Routes = [
  {
    path: 'base-rule',
    loadComponent: () => import('./content/base-rules/base-rules.component').then((c) => c.BaseRulesComponent),
    providers: [OrderFacade, OrderLineFacade, provideBaseRuleEngine()],
    children: [/* … */],
  },
];
```

Any `BaseEntityFormComponent` rendered under that route will now:

- Ask the adapter for rules whose `context` equals the current entity's name.
- Evaluate each enabled rule via `BaseRuleEvaluatorService`.
- Attach `RuleEvaluationResult` errors/warnings to the form fields listed in `rule.fields`.

### 2. Add the rule-authoring UI

Two options:

**a) Use the bundled routes** — mount `BASE_RULE_ROUTES` where you want the rule list/detail views to live:

```typescript
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';

export const APP_ROUTES: Routes = [
  { path: 'admin', children: BASE_RULE_ROUTES }, // -> /admin/base-rule
];
```

**b) Register the facade** — if you want the rule editor to appear alongside other entities managed through `BASE_ENTITY_FACADE_REGISTRY`, provide `BaseRuleFacade` in the appropriate route and expose it under `ACTIVE_ENTITY_FACADE` like any other `BaseEntityFacade`.

Both paths render the same `BaseRuleContainerComponent`, which extends the standard container with a **Dry run…** button.

### 3. Write a rule expression

A rule is just a `BaseRule` record with an `expression` that must evaluate to a truthy value for the rule to pass. The evaluator compiles it as `new Function('entity', '"use strict"; return (…);')`, so:

- The single available variable is `entity` — the object being validated.
- Everything is standard JavaScript. There is no sandbox; only allow trusted authors to edit rules.
- The compiled function is cached per unique expression string.

```jsonc
// A rule that blocks confirming an order with a zero total
{
  "name": "Order total must be positive",
  "context": "Order",
  "expression": "entity.status !== 'CONFIRMED' || entity.total > 0",
  "severity": "ERROR",
  "message": "Confirmed orders must have a total greater than zero.",
  "translocoId": "order.rule.totalPositive",
  "enabled": true,
  "fields": ["total"]
}
```

Field semantics:

- **`context`** – the `entityName` of the target entity (e.g. `"Order"`, matching `OrderFacade.entityName`). The adapter filters by this value when loading rules.
- **`severity`** – `ERROR` blocks form submission; `WARNING` and `INFO` are surfaced but non-blocking (behavior owned by the consuming form).
- **`message` / `translocoId`** – the text (or i18n key) shown when the rule fails.
- **`fields`** – attribute names that should be marked invalid in the form. If omitted, the failure is reported at the form level.
- **`enabled`** – short-circuited to `passed: true` when `false`; useful for staging rules.
- **`extendsRuleId` / `override`** – reserved for rule inheritance and override semantics enforced by the backend.

### 4. Evaluate a rule programmatically

You do not usually need to call the evaluator directly — the base-entity form does that through the engine adapter. But for custom flows (e.g. batch validation, a "run all rules" script), inject the services and drive them yourself:

```typescript
@Injectable({ providedIn: 'root' })
export class OrderValidator {
  private readonly service = inject(BaseRuleService);
  private readonly evaluator = inject(BaseRuleEvaluatorService);

  validate(order: Order): Observable<RuleEvaluationResult[]> {
    return this.service
      .findByQuery({ filters: [{ property: 'context', operator: '==', value: 'Order' }] })
      .pipe(map((page) => (page as { content: BaseRule[] }).content
        .filter((rule) => rule.enabled)
        .map((rule) => this.evaluator.evaluate(order, rule))));
  }
}
```

`RuleEvaluationResult` mirrors what the engine adapter returns, so the same shape flows through both paths.

### 5. Dry-run a rule against a real entity

`BaseRuleContainerComponent` adds a **Dry run…** action to the form. It opens `BaseRuleDryRunDialog` which lets the user pick (or navigate to) an entity of the rule's `context` and shows the resulting `RuleEvaluationResult`. This relies on `BaseFormNavigatorSingletonStore` from `@processpuzzle/base-entity` to jump to the linked entity's list view, so make sure the target entity is registered in `BASE_ENTITY_FACADE_REGISTRY`.

No extra wiring is required — routing `BASE_RULE_ROUTES` into your app is enough.

## Public API summary

Re-exported from `@processpuzzle/base-rule`:

| Symbol | Kind | Purpose |
| --- | --- | --- |
| `BaseRule`, `Severity` | class / enum | The rule record and its severity levels. |
| `RuleEvaluationResult` | interface | Result returned by the evaluator and the engine adapter. |
| `BaseRuleEvaluatorService` | service | Compiles + evaluates a single rule against an entity. |
| `BaseRuleMapper`, `BaseRuleService`, `BaseRuleStore` | infra | Standard `BaseEntity*` triplet backing the CRUD UI. |
| `BaseRuleFacade` | class | `BaseEntityFacade<BaseRule>` — plug into `ACTIVE_ENTITY_FACADE`. |
| `BaseRuleContainerComponent` | component | CRUD UI with Dry-run action. |
| `BASE_RULE_ROUTES` | routes | Drop-in routes mounting the container at `/base-rule`. |
| `createBaseRuleDescriptor(getContextOptions)` | factory | Descriptor for the rule editor form. |
| `BaseRuleEngineAdapter` | service | `RuleEngine` implementation loading rules from the backend. |
| `provideBaseRuleEngine()` | provider | Binds `RULE_ENGINE` to the adapter above. |
