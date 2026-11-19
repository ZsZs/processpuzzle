# @processpuzzle/base-calendar

Angular library of the **base-calendar** feature: team and resource calendars, placed in an application as
widgets. Its backend half is `base-calendar-backend`.

**Status: scaffold.** Only the translation scope exists; the store, the service and the widgets follow the
phases of [docs/calendar/base-calendar-design-plan.md](../../../docs/calendar/base-calendar-design-plan.md).

## Layers

Two folders under `src/lib`, as in base-chat. `feature` builds on `domain`, never the other way round; an ESLint
`no-restricted-imports` rule in `eslint.config.mjs` enforces it, mirroring the backend's `HexagonalLayerRules`.

| Layer | Will contain |
| --- | --- |
| `domain/` | Types, the REST service, the signal store |
| `feature/` | The `pp-calendar` and `pp-calendar-upcoming` widgets, the entry dialog, the details panel |

FullCalendar is imported by one wrapper component of `feature/calendar` only.

## Translations

Scope `base_calendar`, registered with `alias: 'base_calendar'` (see `BASE_CALENDAR_TRANSLOCO_ALIAS`).
