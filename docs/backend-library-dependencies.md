# Backend library dependencies

The ten backend libraries in `libs/java-shared` are meant to be usable apart from one another: a host
application composes the subset it needs, and each library is a Spring Modulith application module whose
`allowedDependencies` are verified at build time by its own `ModularityTests`. This document records
**which libraries actually reach for which**, and — more usefully — **by what mechanism**, because the
mechanism is what determines whether an edge can be removed later without touching a feature.

Derived from the state of the tree at commit `f05a841c` (2026-09-04): the Maven `pom.xml` dependency
edges, every `import com.processpuzzle.*` in `src/main`, every `publishEvent` call and every
`@EventListener` / `@TransactionalEventListener` in the workspace.

> **Updated 2026-09-04**, after Phase 0.2 of the [platform-admin extraction](platform-admin-extraction.md):
> the `org-admin → platform-admin` edge is gone. It was the only compile edge between two feature
> libraries that was not a base-* feature, and three remain — all of them among base-state,
> base-workflow, base-entity and base-rule.

## The matrix

Rows are the **consumer**, columns the **provider**. `·` means no relationship of any kind.

| consumer ↓ / provider → | entity | rule | state | workflow | app | document | widget | platform-admin | org-admin | store |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **base-entity** | — | · | · | · | · | · | · | · | · | · |
| **base-rule** | · | — | · | · | · | · | · | · | · | · |
| **base-state** | **I + B + E** | · | — | · | · | · | · | · | · | · |
| **base-workflow** | · | **I + B** | **I + B** | — | · | · | · | · | · | · |
| **base-app** | **P⁰** | **P** | · | · | — | · | · | **E + P⁰** | · | · |
| **base-document** | · | · | · | · | · | — | · | · | · | · |
| **base-widget** | · | · | · | · | · | · | — | · | · | · |
| **platform-admin** | · | · | · | · | · | · | · | — | · | · |
| **org-admin** | · | · | · | · | · | · | · | **P** | — | · |
| **processpuzzle-store** | · | · | · | · | · | · | · | · | · | — |

| Symbol | Mechanism | Cost of the edge |
| --- | --- | --- |
| **I** | Direct import — a Maven `pom.xml` dependency and a compile-time reference to the other library's type | The consumer is undeployable without the provider on the classpath |
| **B** | Spring bean injection — a bean whose type the provider owns is injected into the consumer | Same as **I**, plus a run-time wiring requirement, unless injected through `ObjectProvider` |
| **E** | Domain event — the consumer observes an event the provider publishes | None, when the event type lives in `api-contracts` (see below) |
| **P** | Outbound port — the consumer declares an interface; the *application* supplies the adapter | None: no compile-time edge, and the port has a safe default |
| **P⁰** | A declared port for which **no adapter exists anywhere**, so the permissive default always applies | None; latent capability rather than a dependency |

Two libraries are omitted from the matrix because every row would name them:

- **`processpuzzle-core`** — Modulith module `type = OPEN`. Every library imports from it (`LogClass`,
  the i18n bundle support, `RsqlSpecificationBuilder`, `SortParser`, `OrganizationGuard`,
  `KeycloakAdminClient`, `ApiAdviceOrder`). Being open, consumers need not declare it as an allowed
  dependency, but by convention they all do, which keeps the edge visible.
- **`api-contracts`** — the generated `com.processpuzzle.<feature>.api` / `.model` DTOs each feature
  serves, plus the hand-written `shared :: event` package.

## The three compile-time edges

These are the only places where one feature library names another in its `pom.xml`.

### base-state → base-entity  (`I + B + E`)

The deliberate one, and the only edge documented as intentional in the consumer's own
`package-info`. base-state is the **single writer** of an object's current-state attribute, and that
attribute lives on a base-entity-managed `EntityObject`; keeping the edge in this direction is what
guarantees there is no second path by which a state could be written. The price is that
base-state-backend is not deployable standalone.

- **Injection** — `EntityObjectAccess` into `BaseEntityObjectGateway` (reads and the single-attribute
  compare-and-swap write), `EntityAttributeQuery` into `StateMachineTopologyValidator` (checking that
  `stateAttributeKey` names a TEXT- or ENUM-valued attribute). Both reached through base-state's own
  outbound port `EntityObjectGateway`, so a host application may still substitute its own store.
- **Event** — `EntityObjectCreatedListener` observes `EntityObjectCreatedEvent` to write the initial
  state onto a new object. It observes *only* that event: `EntityObjectUpdatedEvent` would loop, since
  writing the attribute is itself an update.
- Named interfaces used: `baseentity :: operations`, `baseentity :: event`.

### base-workflow → base-rule  (`I + B`)

`BaseRuleEvaluationAdapter` implements base-workflow's own `RuleEvaluationPort` on top of base-rule's
`EvaluateObject`, injected as `ObjectProvider<EvaluateObject>`. Absent the provider, every
precondition/postcondition check passes rather than the module failing to start. Named interfaces:
`rule :: usecase`, `rule :: domain`.

### base-workflow → base-state  (`I + B`)

`WorkflowStateGatewayConfig` injects `ObjectProvider<StateOperationApi>` and builds either
`BaseStateEntityStateGateway` or, with a one-line startup warning, `UnavailableEntityStateGateway`.
Named interfaces: `state :: domain`, `state :: operations`.

### org-admin → platform-admin — **removed 2026-09-04**

It was the fourth. `TenantRealmResolver` injected `FindOrganization` to resolve an `orgKey` to a
realm and to reject unknown or suspended tenants before the user directory was touched, and
`OrgAdminApiExceptionHandler` imported `OrganizationNotFoundException`.

It is now `org-admin`'s own outbound port `TenantRealmDirectory`, whose default maps a key to a
realm of the same name and calls it administerable — the platform's naming rule, and therefore the
correct answer wherever no tenant registry is deployed. The adapter over `FindOrganization` belongs
to whichever application composes org-admin beside a registry. `UnknownOrganizationException`
replaced the imported type; the `organization.not-found` error id is unchanged, since the id belongs
to org-admin-api.yaml rather than to the class. `org-admin`'s `allowedDependencies` are now
`{"core", "shared"}` and its `pom.xml` names no feature library.

Note that in the two `base-workflow` edges the provider is injected through `ObjectProvider`, which
makes the *run-time* requirement optional even though the compile-time one remains. base-state
injects its provider directly, so for it the dependency is hard in both senses.

## base-app depends on no feature

base-app-backend names no other feature library in its `pom.xml`. What it needs from outside itself
it declares as an outbound port in `app :: port`, and the deploying application supplies the adapter:

| Port | Question it asks | Adapter |
| --- | --- | --- |
| `TenantDirectory` | Does this tenant exist, and what is its locale? | **None in this repository** — the adapter was dropped in Phase 0.1 of the extraction, so that no application here names platform-admin. `processpuzzle-admin-backend` may declare it again |
| `RuleEvaluator` | What do this tenant's governance rules say? | Same file, over base-rule's `EvaluateObject`, translating severity across two structurally identical enums by name |
| `EntityNameRegistry` | Which entity names exist? | **None anywhere** — the permissive default always applies |

The one wired adapter projects narrowly on purpose, as the dropped one did: `RuleEvaluator` hands
base-app its own `Severity`, and `TenantDirectory.Tenant` carried an `orgKey` and a locale rather
than platform-admin's `Organization` aggregate, so a provider's types never re-enter base-app's
signatures. Splitting these modules into services is then a change to one file in the application.

base-app's only in-library link to another feature is the event pair below.

## Events

Only two event flows cross a library boundary today.

| Event | Publisher | Subscriber | Phase |
| --- | --- | --- | --- |
| `OrganizationProvisionedEvent` | platform-admin `ProvisionOrganization` | base-app `StarterAppCreator`; platform-admin's own `OrganizationRealmProvisioner` | `BEFORE_COMMIT` for tenant-scoped writes; `AFTER_COMMIT` for the realm |
| `OrganizationDeletedEvent` | platform-admin `DeleteOrganization` | base-app `TenantDataCleaner`; `OrganizationRealmProvisioner` | as above |
| `EntityObjectCreatedEvent` | base-entity `CreateEntityInstanceUseCase` | base-state `EntityObjectCreatedListener` | after commit, in a transaction of its own |

The first two carry **no dependency cost at all**, because the event classes live in
`api-contracts/.../shared/event` rather than with their publisher. That placement is the point: a
subscriber that has to compile against the publisher's library to read an event is coupled to the
publisher's *implementation*, not to its contract. base-app's entire knowledge of platform-admin is
"delete my tenant-scoped rows when this arrives" — and while the event class was platform-admin's,
expressing that meant a Maven dependency on the whole module. Each event's Javadoc names the
`@TransactionalEventListener` phase a subscriber must use, and getting it wrong fails silently.

`EntityObjectCreatedEvent` still lives in base-entity (`baseentity :: event`), so base-state's
subscription rides on the compile edge it already has for other reasons.

**Published but unobserved.** `EntityObjectUpdatedEvent`, `EntityObjectDeletedEvent`,
`EntityObjectStateChangedEvent`, `DocumentPublished`, `DocumentUnpublished` and the workflow
execution events (`WorkflowInstanceStarted/Completed/Cancelled`, `TaskActivated/Completed/Skipped`,
`ArtifactInstanceCreated`) have **no subscriber outside their own library**, and no application
subscribes to anything. They are headroom for the event-driven integration the
[README](../README.md#event-driven-feature-integration) describes as the target, not wiring that is
carrying load today. `TaskCompletionStateTriggerListener` is the one intra-library exception: it
observes base-workflow's own `TaskCompletedEvent` and then calls base-state through the gateway.

## Independent libraries

`base-document`, `base-widget` and `processpuzzle-store` reach for nothing but `processpuzzle-core`
and `api-contracts`. `processpuzzle-store` is not referenced by any other backend library — the
frontend talks to it over REST, and base-document carries no storage port (`DocumentAccessPolicy` is
a policy hook, not an object-store gateway).

## Reproducing this

```bash
# Maven edges between libraries
cd libs/java-shared
for d in */; do echo "== ${d%/}"; grep -oE '<artifactId>[^<]+' "${d}pom.xml" | sort -u; done

# Index every class to its owning library, then resolve each cross-library import against it.
# An import that resolves to no entry is an api-contracts generated type.
find . -path '*/src/main/java/*' -name '*.java' | sed 's|^\./||' \
  | awk -F'/src/main/java/' '{f=$2; gsub(/\.java$/,"",f); gsub(/\//,".",f); print f" "$1}' \
  | sort > /tmp/class-index

grep -rn 'publishEvent' --include=*.java */src/main
grep -rn '@EventListener\|@TransactionalEventListener' --include=*.java */src/main
```

`/actuator/modulith` serves the same structure at run time, and each library's `ModularityTests`
fails the build if a reach into another module's internals is added without declaring it.
