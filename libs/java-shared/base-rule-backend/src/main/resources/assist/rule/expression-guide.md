## Task: write the expression of a business rule

A rule is ONE JavaScript expression. The platform evaluates it every time an object of the rule's entity is saved. The
expression states what must HOLD: `true` means the object is valid, `false` means the rule is violated and the user sees the
rule's message. So write the condition that valid data satisfies, not the condition that describes an error.

Answer with the bare expression as the `candidate`: no `entity =>` wrapper, no trailing semicolon, no comments, no markdown
fences, no explanation inside it. It may span several lines.

### What the expression can see

- `entity` is the object being saved, read-only. Its attributes are properties named exactly as in the context list
  (`entity.total`, `entity.customer.name`). Use only attribute names the context lists. Never invent one.
- A list attribute is an array: use `every`, `some`, `filter`, `map`, `reduce`, `length`. A component embedded in the object is
  a nested object, or an array of them.
- An attribute with no value is `null` or absent. An unguarded `entity.items.every(...)` throws when there is no list, and
  a thrown error is a failed rule, not a passed one. Guard optional attributes: `!entity.items || entity.items.every(...)`,
  or `entity.note == null || ...` when the rule only applies if the value is present.
- Text, numbers and booleans are plain JavaScript values. Dates are text in ISO form: `'2025-01-15'` for a date,
  `'2025-01-15T10:00:00Z'` for a date-time. ISO dates compare correctly as text (`entity.end >= entity.start`); use
  `new Date(entity.end)` only when you need date arithmetic.
- An ENUM or REFERENCE attribute is text (the enum value, or the identifier of the linked object).
- The result must be a strict boolean. `entity.name` alone (truthy) is NOT a valid rule, but `!!entity.name` or
  `entity.name != null && entity.name !== ''` is.
- Allowed: operators, the ternary operator, arrow functions, regular expressions, `Math`, `Date`, `Number`, `String`, `Array`
  and `Object` methods. Not allowed: statements (`if`, `for`, `var`), assignments, `require`/`import`, I/O, timers, anything that
  changes `entity`. Use array methods instead of loops.

### Measured values: quantities, money and dimensions

An attribute listed as QUANTITY, MONEY or DIMENSIONS holds a measure. Never compare its parts by hand and never use the plain
operators `<`, `>`, `==` on it: they are refused. Read the stored value (or a literal) with a helper, then compare with a method.

- `qty(entity.weight)` or `qty('5 kg')`; `money(entity.price)` or `money('100 EUR')`; `dims(entity.size)`.
- Compare with `.gt(x)`, `.ge(x)`, `.lt(x)`, `.le(x)`, `.eq(x)`, where `x` is another `qty(...)` / `money(...)`. The units are
  converted for you, so `qty(entity.weight).le(qty('2 kg'))` is right whatever unit the weight was stored in.
- Calculate with `.plus(x)`, `.minus(x)`, `.times(number)`, `.dividedBy(number)`; convert with `.to('g')` (money: `.to('USD')`).
- `qty(null)` is `null`, so guard an optional measure: `entity.weight == null || qty(entity.weight).le(qty('2 kg'))`.
- A literal needs a unit or currency from the attribute's listed options; compare quantities of the same kind only.
- `.amount` and `.unit` (money: `.currency`) read the parts as text; avoid them unless the description asks for exactly that.

### Examples

Description: Every line item quantity must be greater than zero.
`!entity.lineItems || entity.lineItems.every(li => qty(li.quantity).gt(qty('0 pc')))`

Description: The end date may not be before the start date. (both optional)
`!entity.startDate || !entity.endDate || entity.endDate >= entity.startDate`

Description: A discount is only allowed for customers of type GOLD.
`!entity.discount || entity.customerType === 'GOLD'`

Description: The e-mail address must look like an e-mail address, if one is given.
`!entity.email || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(entity.email)`

Description: The order total must not exceed 10000 euros.
`entity.total == null || money(entity.total).le(money('10000 EUR'))`

Description: At least one line item is required.
`!!entity.lineItems && entity.lineItems.length > 0`

Description: The title must be between 5 and 80 characters long.
`!!entity.title && entity.title.length >= 5 && entity.title.length <= 80`

Description: If the status is CLOSED, a closing date is required.
`entity.status !== 'CLOSED' || !!entity.closedOn`

Description: The sum of the line amounts must equal the total.
`!entity.lineItems || entity.lineItems.reduce((sum, li) => sum + (li.amount || 0), 0) === entity.total`

### When the description is vague

Pick the most natural reading and say what you chose in `assumptions` (one short sentence each), for example "Zero is not
counted as positive" or "A missing end date is accepted". In `explanation`, say in one or two sentences, in the user's own terms,
when the rule passes. When the current value is given and the description asks for a change, start from it and change only what
the description asks for.
