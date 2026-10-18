# Document vs. Report Template: Modeling and Persistence

*2026-10-13*

## Question

I have a new dilemma for today. I already have the "Document" concept. A user can define a wiki-page-like document from free text, graphics, and widgets. Although the content can be changed by time it is considered static in terms, that there is only 1 document, with the given content. Another document has different content.
On the other hand I plan to implement embeddable fields in document, which can be updated based on selected object, but it's still fixed, which entitie's value is displayed.

I have the requirement, to provide kind of reports. The tooling is the same, mixed text, pictures, widgets and data from one or more entities. Here the "document" is a template and provides many "thousands" of instances.

The first dilemma is: Are the Document and Report Templates the same, because the tooling is the same?
Should I save the result of a template or suppose that the same data produces the same report? The PDF version will be saved, but I'm speaking about the runtime generated reports.

## Answer

Short answers: **Document and Report Template should share one authoring engine and one content model, but they should be two different concepts in your domain.** And **don't persist the rendered runtime report by default; persist the template, the parameters, and a version reference, and snapshot only when you need an audit trail.**

### 1. Same thing or not?

The tooling being identical is an argument for sharing the *editor and renderer*, not for merging the concepts. The difference lies in the binding of data:

| | Document | Report Template |
|---|---|---|
| Cardinality | 1 definition = 1 output | 1 definition = N outputs |
| Data binding | Fixed (this field shows *that* entity's value) | Parameterized (the root object is an input) |
| Identity | The content *is* the thing | The output is a function of (template, parameters, data) |
| Lifecycle | Edited, versioned | Edited, versioned, *instantiated* |

A Document with fixed embedded fields is really just the degenerate case of a template: every binding is a constant. That suggests a clean model:

- **Content definition** (shared): a block tree of text, images, widgets, and data bindings. This is what your editor produces.
- **Binding** is the part that varies: a field points to either a *fixed reference* (Document) or a *parameter/context path* (Report Template, e.g. `$order.customer.name`).
- **Document** = content definition + all bindings resolved statically.
- **Report Template** = content definition + declared parameters (e.g. `orderId`, or a date range) + data queries.
- **Report Instance** (runtime) = template version + parameter values (+ optionally a snapshot).

So the practical answer is a shared "content definition" type, with a *kind* or mode that decides whether it declares parameters. If you later want to "turn this document into a template", it becomes just promoting some fixed bindings to parameters. That keeps the editor single, which is where the real cost is.

One thing to watch: the moment you add parameters, you also need things a Document never needed: iteration blocks (repeat a row for each line item), aggregations, conditional sections, paging/layout rules for PDF. Those belong on the template side, and you can hide them from the Document authoring UI.

### 2. Save the generated report or regenerate?

"Same data produces the same report" only holds if all of these are stable, and in practice they usually aren't:

- **Data changes**: an invoice-like report viewed a year later must show what it showed then, not today's values. Entity data is mutable.
- **Template changes**: someone edits the template, and old reports silently change.
- **Code changes**: renderer or widget updates alter output.
- **Calculations and ranking logic** (your sail race results, say) may change when rules are corrected.
- **Non-determinism**: "now", relative dates, locale, time zone, permissions of the viewing user.

A useful split is by the question "does this report have to be reproducible later?":

1. **Live/exploratory reports** (dashboards, "my current standings", ad hoc views): regenerate on demand, store nothing except perhaps a cache. Persist only the *report request*: `templateId`, `templateVersion`, parameters.
2. **Official/point-in-time reports** (what you handed to someone, published results, anything with legal or dispute value): store a snapshot. Your saved PDF already covers the visual side, so that is probably your answer here.

For the middle ground, there are two reasonable snapshot depths:

- **PDF only** (what you plan): cheap, immutable, but not queryable or re-renderable in another format.
- **Resolved data snapshot** (the JSON produced after bindings and queries are evaluated, before layout): small, lets you re-render to HTML, PDF, or a new layout later, and gives you an audit trail of exactly what the numbers were. I would store this alongside the PDF for the reports that matter.

Whichever you choose, make sure **templates are immutable once used**: publishing creates a new version, and an instance references a specific version. That makes regeneration at least *template-stable*, and cheap to explain: "same template version + same parameters + data as of the generation time."

### Suggested rule of thumb

- Model **ReportTemplate** (versioned, parameterized) and **ReportRun** (templateVersion, parameters, requestedBy, timestamp, status).
- Default: no stored output, render on demand.
- Per template, a flag such as `retention: none | snapshot | pdf+snapshot`, so officially issued reports are frozen and everything else stays live.
- Keep Document as its own concept in the UI and API, but implement it on the same content-definition engine.

### Open question

For your sail race use case, are the results reports something people will later cite or contest (final standings after a regatta)? If yes, that is a clear case for the snapshot flag, since later rule corrections shouldn't silently rewrite a published result.

---

## Decisions (2026-10-18)

The trigger is the invoice: its data lives in an `Invoice` entity, the paper/PDF invoice is generated from it,
and authoring the layout needs exactly the Document tooling.

1. **A Report Template is a kind of Document**, not a separate aggregate. `Document` gains
   `kind: DOCUMENT | REPORT_TEMPLATE`. Ports, locales, draft/publish and revisions are reused unchanged; the UI
   and API present the two kinds separately where that helps (list filter, editor affordances).
2. **The difference between the kinds is *when* entity input ports are bound.** A `DOCUMENT` binds them at
   authoring time (the port's `defaultValue` is a fixed object id, or the hosting page supplies it). A
   `REPORT_TEMPLATE` declares them as **parameters**, supplied per run.
3. **First version renders in the browser**, PDF through the browser's print-to-PDF of a print-styled report
   view. Server-side rendering, batch runs and stored snapshots (`ReportRun`, `retention`) stay as designed
   above but are a later phase.
4. **Electronic invoices are out of scope for base-document.** UBL, ZUGFeRD or NAV Online Számla XML are
   mappings onto a fixed data schema, not layouts — an export format of the entity, not a template.

## Entity binding

The binding model already exists in base-document and base-widget; what was missing is resolution.

### Data sources are ports, declared once

A document's `inputPorts` are its data sources. Fields and tables never carry their own entity type or query —
they point at a port and a path. One invoice has ~20 fields; per-field sources would mean 20 fetches with no
guarantee they show the same invoice.

| Binding case | Port type | Example |
| --- | --- | --- |
| a) one object | `ENTITY_REF` (`entityType` + object id) | `invoice` |
| b) query of objects | `ENTITY_COLLECTION` (`entityType` + `defaultRsqlFilter`) | `openOrders`, `status!=CANCELLED` |
| c) a collection inside an object | *no port* — a path below an `ENTITY_REF` | `invoice.lineItems` |

Case c) is the invoice's line items: an `EMBEDDED_COMPONENTS` attribute of the root object, not a separate query.

`entityType` is the entity **name** (`Order`), as everywhere else in ports; the resolver maps it to the definition
`code` through `EntityDefinitionRegistry` and fetches via `/organizations/{orgKey}/entities/{code}`.

**Parameterized filters.** An `ENTITY_COLLECTION` filter may reference another port: `customerId==${customer.id}`.
`${port.path}` is substituted before the query runs; a reference to an unresolved port leaves the collection empty
rather than running an unfiltered query.

### Paths

A path is `port` or `port.attr.attr…`, walking embedded objects and arrays by attribute name
(`invoice.customer.address.city`). Relationship (foreign-key) attributes are **not** followed in the first
version — the path stops at the stored value (the id). Following them is a resolver extension, not a model change.

### Placing data in a document

1. **Individual value — the `dataField` inline node.** A Tiptap inline atom node with attributes
   `{ path, format }`, so a value can sit inside a sentence ("Dear {invoice.customer.name},"). It is text-level,
   not a WIDGET block: it has no props schema, ports or registry entry, and it inherits the paragraph's styling.
   `format`: `text` (default), `number`, `currency:<ISO code>`, `date`, `datetime` — rendered with the document's
   locale. An unresolved path renders as an empty, outlined placeholder in the editor and as nothing in the reader.
2. **Table — the `entity-table` widget.** A regular widget (registry, props schema, contract spec):
   - `rows` — bound through `inputBindings` to a path: a collection port (`openOrders`) or a collection inside an
     object (`order.lineItems`);
   - `columns` — ordered `{ path, header, format, align }`, `path` relative to one row;
   - `totals` — optional `{ path, aggregate: sum | count }` per column, rendered as a footer row;
   - `caption` — optional.

   Columns are widget props rather than the port's `attributeVisibility`: they need an order, labels and
   formats. `attributeVisibility` stays what it is — a projection of what the port exposes, which the column
   picker offers.

   The widget receives *resolved rows*; the path lives in the block's `inputBindings`, so paths stay a document
   concern and the widget is usable anywhere `rows` can be supplied. This is why an `inputBindings` value is a
   **port path**, not only a port name — the backend validates its first segment against the declared ports.
   The document editor configures the block through a *Table settings* dialog (source, columns with header,
   format and order, totals), since the generic props form cannot edit arrays of objects yet.

### Resolution

`DocumentDataContext` (frontend, base-document) replaces `DocumentContentStore.resolveBinding()`'s placeholder:

1. receives the **port values** from the host: `{ portName: objectId | rsqlOverride }`;
2. for each `ENTITY_REF` port: fetches the object; for each `ENTITY_COLLECTION` port: substitutes `${…}`
   references, then queries (`defaultRsqlFilter` unless the host supplied a filter);
3. exposes `resolve(path)` as a signal-backed lookup that `dataField` nodes, `entity-table` and the existing
   widget `inputBindings` all use — one resolution rule, as for widgets today.

Missing required ports are reported once (a banner in the reader) instead of per field.

### Running a report

Implemented 2026-10-18; `order-invoice` in the testbed's sample documents exercises every part.

- **Preview tab** (`document/<id>/preview`): the *draft* rendered against data. Port values are query
  parameters — `?order=<id>` — the same pattern the signing tab uses for `subjectType` / `subjectId`; a parameter
  bar (one object picker per `ENTITY_REF` port) writes them back, so the author designs against real data and a
  preview of one invoice is a link. The editor itself resolves nothing: an unresolved field shows its path.
- **PDF:** *Print / Save as PDF* prints a copy of the rendered page in a hidden iframe, with the page's style
  sheets and `@page` margins — the application shell around the report is not base-document's to hide. No new
  PDF dependency.
- **Reports tab** (`<entity>/<id>/reports`): `provideEntityReportsTab()` offers it, through the entity tab
  contributors, to every entity some `REPORT_TEMPLATE` has an `ENTITY_REF` port of; it renders the *published*
  template with the current object bound (*Print invoice* on an Order).

### Not in the first version

Server-side rendering, batch runs ("1000 invoices" = case a) run N times), `ReportRun` and snapshot retention,
conditional sections, repeating sections other than tables, following relationships in paths, page headers/footers
and page breaks beyond what CSS print rules give.
