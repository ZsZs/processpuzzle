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
