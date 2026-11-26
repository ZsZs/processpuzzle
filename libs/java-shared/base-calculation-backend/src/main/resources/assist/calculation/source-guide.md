## Task: write the source of a calculation

A calculation is a small graph of decisions, written as one YAML document and evaluated with FEEL (the DMN expression
language). The user has written the top of the document: the `calculation` id, a `name` and a `description` in their own
words. Write the whole document from that description. Answer with the complete YAML document as the `candidate`: no markdown
fences, no text before or after it.

Keep the user's `calculation`, `name` and `description` keys exactly as they are. Name only the entities and attributes
the context lists. Never invent an entity or an attribute. Indent with two spaces. Write each FEEL expression in double
quotes; FEEL strings inside it use single quotes, or escape the double quotes.

### The document

```yaml
calculation: order-total               # the id, kept
name: Order total                      # kept
description: ...                       # kept
parameters:
  order: { entity: order }             # an entity parameter: the calculation gets one object of that entity
  customerTier: { type: string, default: STANDARD }   # a plain parameter: string, number, boolean, date or date-time
inputs:
  lines: { from: order, path: lineItems, entity: order-line }   # the rows embedded in an entity parameter's object
  # finishes: { entity: finish, where: "series==${series}" }    # or the objects of an entity found by an RSQL filter
tables:
  volume-discount:
    hitPolicy: FIRST                   # FIRST, UNIQUE, ANY or COLLECT
    inputs: [subtotal, customerTier]   # FEEL expressions (here: decision and parameter names)
    output: discountRate
    rules:
      - { when: ['>= 10000', '"GOLD"'], then: 0.10 }   # one unary test per input; '-' matches anything
      - { when: ['-', '-'],              then: 0 }
functions:
  gross: "function(net) net * 1.2"     # optional FEEL functions
decisions:
  lineTotal:    { for: lines, expr: "quantity.baseAmount * unitPrice.baseAmount" }   # one value per row
  subtotal:     { requires: [lineTotal], aggregate: { sum: lineTotal } }              # one value
  discountRate: { requires: [subtotal, customerTier], table: volume-discount }
  total:        { requires: [subtotal, discountRate], expr: "round half up(subtotal * (1 - discountRate), 2)" }
output:
  entity: order                        # the entity written; the one entity parameter of that entity is the target
  mapping: { total: total }            # attribute of the entity: the decision (or parameter) its value comes from
  materialization: stored              # stored (written on every run) or onRead
trigger:
  onChange: { entity: order }          # recompute when an object of the entity is created or changed
  freeze: { state: { entity: order, to: CONFIRMED } }   # optional; or `freeze: onCompletion`
```

### Rules of the graph

- Entities are named by their **code**, as the context lists them (`order`, not `Order`).
- Every decision has exactly ONE of `expr`, `table`, `aggregate` or `rank`, and a scope given by `for`:
  - no `for`: one value. It sees the parameters, the inputs (as lists of rows), the functions and earlier single values.
  - `for: <input>`: one value per row. It also sees the row's attributes by name, and adds its value to the row as a column.
  - `for: <attribute>` (or `<input>.<attribute>`): one value per distinct value of the attribute, i.e. per group.
- `requires` lists the decisions (and parameters) a decision reads, so the graph is ordered. Name every decision an expression uses.
- Decision, parameter, input, table and function names are identifiers (letters, digits, `_`; no spaces, no `-`).
- `aggregate` is exactly one of `count`, `countDistinct`, `sum`, `avg`, `min`, `max`, over a column (an attribute or an earlier
  row decision) or a FEEL expression. Options: `partitionBy` (a per-partition value broadcast to each row), `dropWorst` and
  `dropBest` (a number or a decision's name; `better: lower` is the default, `higher` flips which values are worst), and `of`
  (the rows of a single-valued aggregate, when `requires` does not make it clear).
- `rank` is competition ranking (1, 2, 2, 4): `by` a column, `order: asc|desc`, optional `partitionBy`, a FEEL `filter` (a row
  failing it gets no rank) and `tieBreak` columns (`name` or `name desc`).
- A grouped output writes one object per group: `output: { entity: ..., for: <attribute>, key: [attributes that find the existing
  object], mapping: { ... } }`, and every `key` attribute is mapped.
- `trigger` is `onChange: { entity }`, `state: { entity, to }` or `manual: true`; the entity must be the entity of one of the
  parameters. Leave the trigger out when the description does not say when to recompute.

### FEEL

- Numbers are decimals; text is `"double quoted"`; booleans `true` / `false`; `null` for no value. Comparison is `=`, `!=`, `<`,
  `>`; logic is `and`, `or`, `not(x)`; conditions are `if c then a else b`.
- Dates and date-times in attributes of kind DATE / DATE_TIME are real FEEL dates: subtract them for a duration, compare them
  directly. Write a literal as `date("2025-01-15")`.
- Useful built-ins: `sum(list)`, `count(list)`, `min(list)`, `max(list)`, `mean(list)`, `round half up(n, scale)`,
  `round half down(n, scale)`, `floor(n)`, `ceiling(n)`, `abs(n)`, `string length(s)`, `contains(s, part)`, `upper case(s)`,
  `list contains(list, x)`, `string(x)`, `number(s)`. Platform additions: `drop worst(list, n)`, `drop best(list, n)`,
  `top(list, n)`, and `convert currency(amount, "USD", "EUR", date)`.
- A path reads into an object: `order.total`, `customer.address.city`.

### Quantities, money and dimensions

An attribute listed as QUANTITY, MONEY or DIMENSIONS is an object. Read it as a number through its derived fields, never through
`amount`: `weight.baseAmount` (the amount in the base unit, so grams and kilograms add up), `price.baseAmount`,
`size.baseArea`, `size.baseVolume`. An output can write a bare number into such an attribute; it is taken to be in the
attribute's default unit or currency.

### More examples

A per-row duration and a ranking per round, from a series of races (`finishes` are the objects of the entity `finish`):

```yaml
parameters:
  series: { entity: series }
inputs:
  finishes: { entity: finish, where: "series==${series}" }
decisions:
  elapsed:  { for: finishes, expr: "finishTime - startTime" }
  position: { for: finishes, requires: [elapsed], rank: { by: elapsed, order: asc, partitionBy: round, filter: 'status = "OK"' } }
```

A total per group, with the worst values dropped:

```yaml
decisions:
  seriesTotal: { for: boat, requires: [roundPoints], aggregate: { sum: roundPoints, partitionBy: boat, dropWorst: 1 } }
output:
  entity: series-result
  for: boat
  key: [series, boat]
  mapping: { series: series, boat: boat, total: seriesTotal }
```

### When the description is vague

Choose the most natural reading and list each choice in `assumptions`, one short sentence each, for example "The discount
applies to the subtotal, before tax". In `explanation`, say in two or three sentences, in the user's own terms, what the
calculation computes and where it writes the result. When the current source already has more than the header, treat the user's
description as a request to change it and keep what the description does not touch.
