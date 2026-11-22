# Fundamental Types — quantities, money and dimensions

Status: plan, 2026-11-20. Successor of the legacy `com.processpuzzle.fundamental_types` package
(ProcessPuzzleFramework): we keep its ideas — a value object of amount + unit, money as a quantity
whose unit is a currency, a time-bound exchange rate — and drop its implementation (`double` amounts,
pairwise ratio graph without transitivity, no notion of what a unit measures).

## Goals
- The user picks a unit from the ones that fit the attribute, and can convert the entered value.
- One consistent control each for quantities, money and 2D / 3D dimensions.
- Rules and calculations compare and combine values without converting units themselves.

## Decisions
| # | Decision |
| --- | --- |
| D1 | Unit catalog: a fixed **standard catalog** shipped with the platform. Organizations add **units to its kinds** (phase 6): factor and kind frozen, retired rather than deleted. New kinds and currencies stay out. |
| D2 | Currency conversion through **exchange rates with validity periods** (phase 8): per organization, entered by hand, an explicit date that defaults to today. Money of different currencies is still never combined or compared without converting first. |
| D3 | Amounts are **decimals**, exact end to end. |
| D4 | Our **own catalog**; no Indriya / Moneta / js-quantities. |
| D5 | Dimensions use **one unit for all axes**. |
| D6 | Durations are the TIME kind and interoperate with the ISO 8601 durations workflows already use. |
| D7 | Switching the unit converts the value by default; *reinterpret* keeps the number. |
| D8 | Decimal arithmetic in TypeScript uses **big.js**. |
| D9 | DIMENSIONS also store a normalized area / volume, so they are searchable by it. |

## Model
- **Quantity kind** — what a unit measures: LENGTH, AREA, VOLUME, MASS, TIME, TEMPERATURE, SPEED,
  COUNT, … A unit belongs to exactly one kind; only units of the same kind convert.
- **Unit** — `symbol` (identity, e.g. `kg`), `kind`, a translation key for the name, and
  `factor` (+ optional `offset`, for °C / °F) relative to the kind's base unit (SI where one exists).
  Conversion is `base = amount × factor + offset`, so any two units of a kind convert directly.
  TIME offers s, min, h, d, wk only — months and years are not fixed lengths.
- **Currency** — ISO 4217 code and its minor units (HUF 0 / 2, EUR 2, JPY 0 …). Not a unit of a kind:
  money never converts until exchange rates exist (D2).
- **Catalog as data** — the standard catalog is one JSON resource read by both the TypeScript and the
  Java side, with a test that fails when the two loaders disagree. Organization units (D1, later) are
  rows of the same shape.

### Stored values (entity payload, JSONB)
| Control | Value |
| --- | --- |
| QUANTITY | `{ amount: "12.50", unit: "kg" }` |
| MONEY | `{ amount: "1999.90", currency: "EUR" }` |
| DIMENSIONS | `{ width: "1.2", height: "0.8", depth?: "0.5", unit: "m" }` |

Amounts are decimal **strings** so JavaScript never rounds them (D3). The backend adds a normalized
number on write — `baseAmount` for a quantity (in the kind's base unit), the amount for money — which
RSQL filtering and sorting use; clients don't send it. DIMENSIONS get `baseArea` (2D and 3D) and
`baseVolume` (3D), in m² / m³.

### Attribute metadata (on `BaseEntityAttribute`, mirrored in the contract and descriptors)
- QUANTITY: `quantityKind`, `allowedUnits` (empty = all of the kind), `defaultUnit`, `scale`.
- MONEY: `allowedCurrencies`, `defaultCurrency`; scale comes from the currency.
- DIMENSIONS: `dimensionCount` (2 or 3), `allowedUnits`, `defaultUnit`; kind is always LENGTH.

Like ADDRESS and GEO_LOCATION these are `FormControlType`s validated by control type; no new
`ValueKind`.

## Behaviour
- **Control** — amount field + unit / currency select. Switching the unit offers *convert* (keep the
  measured value) or *reinterpret* (keep the number); converting is the default.
- **Display** — lists, PDF export, document data fields and the status bar format through one pipe,
  locale-aware (`Intl.NumberFormat`, `style: 'unit'` / `'currency'`). The existing `currency:<code>`
  value format stays for plain NUMBER attributes.
- **Validation (backend)** — amount parses as a decimal; unit exists, is of the attribute's kind and is
  allowed; currency is ISO 4217 and allowed; scale not exceeded; dimensions have 2 or 3 axes as declared.
- **Rules** — both evaluators offer `qty()`, `money()` and `dims()`, which read a literal (`qty('5 kg')`), a stored
  value (`qty(entity.weight)`) or an object literal as an object with `to`, `plus`, `minus`, `times`, `dividedBy`,
  `eq` / `gt` / `ge` / `lt` / `le`, `round()` (money), `area()` / `volume()` (dimensions) — unit-aware, **throwing** on a
  kind or currency mismatch. Two choices differ from the first draft: the helpers are explicit rather than wrapping the
  whole entity (guessing measures by shape would misread embedded rows with `amount` / `unit` fields), and a bare
  operator on a measure is **refused** (`valueOf` throws), since `qty('10 kg') > qty('9 kg')` compares the text and
  `qty('5 kg') > qty('5 m')` ignores the kinds. The backend runs GraalJS; the browser **interprets** a read-only subset of
  JavaScript (an acorn-parsed, allowlisted tree — no `new Function`, so a rule reaches nothing of the page);
  `rule-measure-cases.json` and `rule-expression-cases.json` pin both to the same answers.
- **Calculations (FEEL)** — a measure reaches FEEL as its stored object: `weight.baseAmount` (the kind's base unit),
  `price.baseAmount`, `size.baseArea` / `size.baseVolume`, and `number(price.amount)` for money to the cent. `amount`
  is deliberately not typed as a number — it means nothing without `unit`, and a sum across units would be quietly
  wrong. Writing a result into a QUANTITY / MONEY attribute: a bare number is taken to be in the attribute's default
  unit / currency, rounded to its decimal places and stored with its derived numbers (`MeasureValues.forWrite`).
- **Durations** — a TIME quantity converts to and from an ISO 8601 duration (`PT90M` ↔ `1.5 h`).

## Placement
- Frontend: catalog, value types, decimal arithmetic and the rule helper in `@processpuzzle/util`
  (base-entity, base-rule, base-calculation and base-document all reach it); the controls in
  `base-entity-frontend` next to ADDRESS / GEO_LOCATION.
- Backend: catalog and value types in `processpuzzle-core`; validation and normalization in
  `base-entity-backend`; the rule helper loaded by `base-rule-backend`'s GraalJS context.

## Phases
1. **Catalog and value model** — catalog JSON, TS + Java loaders, parity test, decimal arithmetic,
   conversion, ISO duration bridge. *Done (#1437).*
2. **Frontend controls** — QUANTITY, MONEY, DIMENSIONS controls with their descriptor options, the
   definition → descriptor mapping, display pipes in the list and the PDF export, labels in five languages.
   *Done (#1439).* Document data fields do not format ADDRESS or GEO_LOCATION either; they follow when they do.
3. **Backend and authoring** — *Part 1 (#1445):* contract, attribute fields (`copyFrom`; `ReplaceAttributeUseCase`
   uses it too), definition validation against the catalog, payload validation, `baseAmount` / `baseArea` /
   `baseVolume` normalization on every write, RSQL through those numbers. *Part 2:* the designer's authoring
   fields (dropdowns, chip inputs suggesting the catalog), query-editor fields for the derived numbers, and one
   sample attribute of each control in the testbed seed's `dynamic-entity`, with a sample instance.
4. **Rules and calculations** — *Part 1 (#1448):* the helpers in both rule evaluators and the browser's rule
   interpreter, which replaced `new Function` (SonarCloud `S1523`); the existing dry run supports the helpers, there is
   no separate "converted values" panel. *Part 2:* a calculation's bare number written into a QUANTITY / MONEY
   attribute in its default unit or currency, validated and stored with its derived numbers; reading stays as it was.

5. **Browser check** — the controls checked in a real browser against a mocked backend (#1451: money shown to the minor
   units). The designer's authoring fields are not covered: they are hosted by ProcessPuzzle UI, not the testbed.
6. **Organization units (D1)** — *Part 1:* `UnitCatalog.withUnits` and the `UnitCatalogProvider` port in core; units as an
   org-scoped resource in base-entity-backend (`/organizations/{orgKey}/units`, kind + factor frozen, retire instead of
   delete); the definition validator, payload validation and derived writes read the owning organization's catalog.
   *Part 2 (#1453):* the backend rule engine reads the rule's organization's catalog. *Part 3:* the frontend catalog
   from the backend (`OrganizationUnitCatalog`, loaded with the entity definitions after sign-in — an app initializer would
   ask before there is a token; `UNIT_CATALOG` is a live view of it), retired units hidden from new choices, the browser's
   rule evaluator and the designer's attribute options on the organization's catalog; document fields and entity-table
   cells still name a unit of the organization by its code. *Part 4:* the designer's units screen — an `Organization Unit` branch of base-entity's authoring routes (`/design/entities/organization-unit`, a second tab beside the entity definitions): code, kind, factor and divisor are `createOnly` (fixed once saved), the status is the one thing still editable (retire), no delete; a saved unit reloads the catalog at once and the attribute options read it lazily.

7. **Testbed uses the types** — the Order family: a line's `quantity` is a COUNT quantity (`pc`, `dz`), `unitPrice` and `total`
   are EUR money; `demo-component-one` has a COUNT quantity too. The `order-total` calculation reads `baseAmount`
   (`quantity.baseAmount * unitPrice.baseAmount`; `number(unitPrice.amount)` answered null, because the stored amount is
   already numeric in a row), the order rules use `qty()` / `money()`, the seeded orders and the e2e fixtures carry measures.
   Whatever shows an attribute without knowing its type reads a measure as one: `formatMeasure` (document fields, entity-table
   cells and sums, PDF export, list cells of a calculated attribute, the calculated control).

8. **Exchange rates (D2)** — rates per organization, `from` → `to`, a decimal and the date it is valid from (it holds until the
   next one of the pair starts). A lookup is the direct rate, else the inverse of the opposite pair's (20 places), else a cross
   rate through the alphabetically first currency that has both legs; nothing else, so a missing rate is an error and never a
   guess. A conversion rounds half up to the target's minor units. *Part 1:* the model in core (`ExchangeRate`,
   `ExchangeRates`, `Money.convertTo`) and `@processpuzzle/util` (`ExchangeRates`, `convertMoney`), run against shared parity
   cases. *Part 2:* the organization resource (`/organizations/{orgKey}/exchange-rates`, create / replace / delete, one rate per pair and
   date) in base-entity-backend, the `ExchangeRateProvider` port in core, `money(...).to(code, date?)` in the backend rule engine and
   `convert currency(amount, from, to, date?)` in FEEL (a missing rate fails the calculation: FEEL turns an error value into null). *Part 3:* the frontend loader (`OrganizationExchangeRates`, read with the entity definitions after sign-in),
   `money(...).to(code, date?)` in the browser's rule evaluator — `rule-measure-cases.json` now carries a `rates` table and the
   conversion cases both evaluators run — and a read-only *Also show in* hint in the MONEY control (today's rate, never stored). *Part 4:* the designer's rates screen — an `Exchange Rate` branch of base-entity's authoring routes
   (`/design/entities/exchange-rate`, the third tab of the Entities section): from, to, rate and the date it is valid from; a rate can be
   deleted, since nothing stored carries it; a save, a change or a delete reloads the organization's table at once.

9. **Seeded units and rates** — `default-units/<orgKey>-units.yaml` (`DefaultUnitsLoader`, with `base-entity.loadDefaultEntities`): a
   `units` list and an `exchangeRates` list, create-only like the other seeds — a unit the organization already has, and a rate for
   a pair and date it already has, are left as they are, so a designer's change survives a restart. Loaded ahead of the entity
   definitions, which may name a seeded unit. The testbed's file gives its organization a pallet (500 kg), a bag (25 kg) and a carton
   (six pieces), and EUR to USD (two dates, to show a rate holding until the next) and EUR to HUF — USD to HUF is the derived cross
   rate. With them the units and rates screens, the money hint and the generated e2e list suites have real rows.

Later: nothing planned.
