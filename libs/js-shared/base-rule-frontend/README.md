# @processpuzzle/base-rule

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-rule-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_rule_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_rule_frontend)
[![npm version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-rule?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-rule)

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
- **`BaseRuleEvaluatorService`** – parses and checks a rule's `expression` string once, keeps the result, and interprets it for each entity (and the measure helpers `qty`, `money`, `dims`), returning a `RuleEvaluationResult`. No text becomes code; see [What a rule expression can use](#what-a-rule-expression-can-use).
- **`BaseRuleEngineAdapter`** – implements the `RuleEngine` contract from `@processpuzzle/base-entity`. Loads all enabled rules whose `context` matches a given entity type and evaluates them on demand.
- **`provideBaseRuleEngine()`** – one-line provider that binds `RULE_ENGINE` (declared by base-entity) to the adapter above. Once provided, every `BaseEntityFormComponent` in the route subtree runs the matching rules automatically.
- **`BaseRuleFacade` / `BaseRuleContainerComponent` / `BASE_RULE_ROUTES`** – the drop-in UI for managing rules. The container adds a **Dry run…** button next to the standard form actions.
- **`createBaseRuleDescriptor(getContextOptions)`** – the `BaseEntityDescriptor` driving the rule editor form. Pass a getter that returns the available entity-context names (typically the keys of `BASE_ENTITY_FACADE_REGISTRY`).

## Usage

There are two independent capabilities to enable:

1. **Author rules** – mount the built-in CRUD UI at a route in your application.
2. **Evaluate rules against entities** – provide the rule engine so that any base-entity form runs its rules on submit.

The snippets below are taken from the [Base Rule sample in the testbed](https://github.com/ZsZs/processpuzzle/tree/develop/apps/processpuzzle-testbed-frontend/src/app/content/base-rules).

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

A rule is just a `BaseRule` record with an `expression` that must evaluate to a truthy value for the rule to pass. The evaluator interprets it, so:

- The variable is `entity` — the object being validated — and the measure helpers `qty`, `money` and `dims` (see [below](#quantities-money-and-dimensions-in-a-rule)).
- The language is a read-only subset of JavaScript, sandboxed by construction — see [below](#what-a-rule-expression-can-use).
- The parsed expression is cached per unique expression string.

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

### What a rule expression can use

The browser does not hand a rule's text to `new Function` or `eval`: it parses the expression once (with [acorn](https://github.com/acornjs/acorn)), checks it, and **interprets the tree** against a list of what a rule may name and call. A rule is data a designer authors and every user who opens the form runs, so what it can reach is decided by that list, not by trust: there is no `window`, `document`, `fetch`, `localStorage` or `Function`, and a rule can only read.

- **Syntax** — one expression: literals (including regular expressions and template strings), the usual operators, `?:`, `?.`, `??`, `typeof`, `in`, `...spread`, array and object literals, `new Date(...)`, and arrow functions with an **expression** body (they can be passed around, called immediately, or call themselves through an argument). Not supported, and refused when the rule is compiled with a message naming the construct: assignment and `++`, `function`, `this`, `class`, `new` of anything but `Date`, statements and block bodies, destructuring, `delete`, `void`, `instanceof`.
- **Names** — `entity`, `qty`, `money`, `dims`, `Math`, `Number`, `String`, `Boolean`, `Array.isArray`, `Object.keys` / `values` / `entries`, `Date`, `parseInt`, `parseFloat`, `isNaN`, `isFinite`, `undefined`, `NaN`, `Infinity`.
- **Methods** — the non-mutating ones of arrays (`every`, `some`, `filter`, `map`, `reduce`, `find`, `includes`, `indexOf`, `join`, `slice`, `concat`, `flat` …), strings (`trim`, `split`, `startsWith`, `toLowerCase`, `replace`, `match`, `padStart` …), numbers (`toFixed`), dates (`getTime`, `getFullYear` …) and `RegExp.test`. `push`, `sort`, `splice`, `Object.assign` and the like cannot be called, so a rule never changes the entity.
- **Refused wherever they appear** — `constructor`, `__proto__` and `prototype`, and a function read from a property and passed around (`arr.map(entity.tags.push)`): both are ways to turn a list of allowed calls into the things it excludes.

The backend runs the same text in GraalJS, which supports all of JavaScript; `rule-expression-cases.json` is run against both so the subset above means the same in the browser's dry run as on the server. A rule that needs more than this subset fails to compile in the browser with the reason, rather than answering differently there.

### Quantities, money and dimensions in a rule

A QUANTITY, MONEY or DIMENSIONS attribute holds an object (`{ amount: "6000", unit: "g" }`), and comparing two of them by hand means converting units. Three helpers do that, so a rule asks the question and nothing more:

```js
qty(entity.weight).gt(qty('5 kg'))                  // 6000 g is more than 5 kg
money(entity.price).le(money('100 EUR'))
dims(entity.size).volume().lt(qty('0.5 m3'))        // 120 x 80 x 50 cm is 0.48 m³
!entity.weight || qty(entity.weight).le(qty('10 kg'))   // an absent value reads as null
```

- `qty(x)`, `money(x)` and `dims(x)` take text (`'5 kg'`, `'100 EUR'`), a stored value (`entity.weight`) or an object literal (`{ amount: 5, unit: 'kg' }`); `null` and `undefined` give `null`.
- A quantity has `amount` (decimal text), `unit`, `base` (a number, in the kind's base unit), `to(unit)`, `plus`, `minus`, `times(n)`, `dividedBy(n)` and `eq`, `gt`, `ge`, `lt`, `le`. Money has `amount`, `currency`, the same arithmetic, `round()` (to the currency's minor units) and the comparisons. Dimensions have `width`, `height`, `depth` (`null` for two axes), `unit`, `to(unit)`, `area()` and `volume()` — both quantities, in m² and m³.
- Different kinds (`kg` against `m`), different currencies, an unknown unit or a temperature on an offset scale (`°C` plus `°C`) **throw**: the rule is reported as failed with the reason, never answered wrongly. Money is never converted between currencies.
- **A bare operator is refused**: `qty('10 kg') > qty('9 kg')` would compare the text and `qty('5 kg') > qty('5 m')` would ignore the kinds, so a measure throws when an operator reads it. Use `gt()` and its siblings; `==` between two measures compares identity, so use `eq()`. `String(q)` and template literals print `5 kg`.
- The backend's rule engine offers the same helpers, and `rule-measure-cases.json` is run against both evaluators — a **Dry run…** here says what the backend will say.

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
