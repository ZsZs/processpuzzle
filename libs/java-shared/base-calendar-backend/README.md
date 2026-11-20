# base-calendar-backend

Spring Boot library for the **base-calendar** feature: team and resource calendars with timed, all-day and
recurring entries, attendees and role-based visibility. Published as the Maven artifact
`com.processpuzzle:base-calendar-backend`; its frontend half is `@processpuzzle/base-calendar`.

**Status: scaffold.** The module declaration, layer rules and build wiring exist; the model and the API do not
yet. The design and the phase plan are in [docs/calendar/base-calendar-design-plan.md](../../../docs/calendar/base-calendar-design-plan.md).

## Shape

- Spring Modulith application module `calendar` (`com.processpuzzle.calendar`), allowed to use `core` and
  `shared` only. No other feature library is a dependency; what the module needs from the platform it declares
  as outbound ports and the host application adapts.
- Two parts, `definition` and `entry`, each split into `domain`, `usecases` and `adapters`. The direction of the
  layers is checked by `HexagonalLayerRules` (arch-unit-maven-plugin, at `process-test-classes`), so a violation
  fails `mvn verify`. `HexagonalLayerRulesTest` runs each rule against fixture classes, so a rule that silently
  stops matching fails too.
- `CalendarModularityTests` checks the module's `allowedDependencies`.

## Build

```
npm exec nx test base-calendar-backend
```
