# AI Authoring Assist: Implementation Plan

Status: implemented, 2026-11-26 (PR #1527, merged). Phases 1–5 are built; Phase 6 is not started. Section 7 records where the build departs from this plan. Companion to [implementation-plan.md](implementation-plan.md) (the design assistant chat). The two share the model, subscription, quota, access and audit infrastructure of `base-ai-backend`; they differ in interaction style.

## 1. Goal

Writing a syntactically and semantically valid rule or calculation is hard for a business user. Both carry a `description`. The user describes the rule or calculation in plain words; AI turns the description into a valid artifact in the feature's own language:

| Target | Language | Validated by |
|---|---|---|
| Rule `expression` | PPCL: one JavaScript boolean expression evaluated against `entity` (GraalJS) | `RuleEngine` compile + `POST /rules/evaluate` dry run |
| Calculation `source` | YAML definition with FEEL expressions (DMN semantics) | `CalculationValidator`, `CalculationAnalyzer`, `POST /calculations/{id}/evaluate` preview |

Calculations are **not** JavaScript; the assist produces each feature's own language.

Platform-internal AI help of this kind will be requested again and again (explain a rule, generate test objects, propose entity attributes, translate messages...). The design is therefore a generic **assist task** mechanism, with rules and calculations as its first two tasks.

## 2. Assist vs. chat

| | Design assistant (existing) | Authoring assist (this plan) |
|---|---|---|
| Interaction | Conversation, multi-turn | One shot: input in, candidate out |
| Scope | Many definitions, cross-kind | One field of one definition |
| Writes | Drafts, through tools | **Nothing**. Returns a candidate; the user accepts it into the form |
| UI | Chat panel | Action on a form field |
| Port | `DefinitionAuthoringParticipant` | `AuthoringAssistParticipant` (new) |

Both stay. Rule and calculation also become `DefinitionAuthoringParticipant` kinds (Phase 5), so the chat can author them too.

## 3. Decisions

| Topic | Decision |
|---|---|
| Module | `base-ai-backend`, new package `ai.assist`; `ai` keeps depending only on `core` and `shared` |
| Port | `AuthoringAssistParticipant` in `processpuzzle-core` (`core/assist`), implemented by each feature in its own lib, as `DefinitionAuthoringParticipant` is |
| Knowledge of usage | `ai.assist` knows tasks only by name. Language guides, context and validation belong to the owning feature (same principle as image recognition: AI knows nothing of its usage) |
| Model, cost | The organization's AI subscription (`OrganizationAiSubscription`, `AssistantModels`); no subscription = assist unavailable, button hidden |
| Controls | Reuse `AssistantAccess`, `AssistantQuota`, `AssistantAuditLog`; `maxRepairRounds` from `AssistantLimits` (default 3) |
| Writes | The assist never writes to the database. Its candidate goes into the field, and the user saves the form through the normal validators. The design assistant's draft save is stricter: it refuses ERROR-level findings (Phase 5) |
| Role | Same as editing the target: whoever may edit rules / calculations may ask the assist. No extra role |
| Data sent to the model | Metadata only (descriptors, attribute names and types, units, function catalog, the user's text). Never entity object data. Dry runs use generated sample objects, run server-side, results reduced to issues |
| Untrusted input | The description and validator output are data, not instructions (same system-prompt rule as `DesignAssistant`) |
| Output | Structured output (JSON schema): `candidate`, `explanation`, `assumptions[]`; the loop adds `issues[]` |
| Frontend | Descriptor metadata on the attribute (`aiAssist`), rendered by `base-entity-frontend`; client behind an injection token with a no-op default, so `base-rule` / `base-calculation` frontends never import `base-ai-frontend` |

## 4. Architecture

```
form field "Generate from description"            (base-entity-frontend, descriptor-driven)
   | AI_ASSIST_CLIENT token (base-ai-frontend supplies it; default: unavailable)
   v
POST /organizations/{orgKey}/ai/assist/{task}     (ai-api.yaml)
   v
AuthoringAssist (ai.assist)
   1. participant = participants[task]            404 if unknown
   2. guide   = participant.guide()
   3. context = participant.context(orgKey, request)
   4. loop: model(guide, context, description, previous candidate + issues)
            -> candidate
            -> participant.validate(orgKey, request, candidate) -> issues
            stop when no ERROR issues or maxRepairRounds reached
   5. audit + quota, return {candidate, explanation, assumptions, issues, rounds}
   ^
AuthoringAssistParticipant (core)  <-- RuleExpressionAssist (base-rule-backend)
                                   <-- CalculationSourceAssist (base-calculation-backend)
```

### 4.1 Port

```java
public interface AuthoringAssistParticipant {
    String task();                                        // "rule.expression", "calculation.source"
    String guide();                                       // language reference + worked examples (classpath resource)
    String context(String orgKey, AssistRequest request); // what the candidate may reference, as text/JSON
    List<AuthoringIssue> validate(String orgKey, AssistRequest request, String candidate);
}

public record AssistRequest(String text, Map<String, String> parameters, String current) {}
```

- `parameters` carries the target's other form values the task needs (rule: `context` entity name, `severity`; calculation: `outputEntity`). Each participant documents its own keys and rejects unknown ones.
- `current` is the field's present value, so "fix / extend my expression" works the same way as "write from scratch".
- `AuthoringIssue` is the existing core type (severity, path, text), so validator output is shared with the chat tools.

### 4.2 Rule task: `rule.expression`

- `guide()`: PPCL reference: the `entity` proxy, attribute access by name, allowed globals, quantity / money / unit helpers (`MeasureBindings`), what is forbidden (side effects, I/O, loops), 10-15 description-to-expression examples taken from the seeded rules.
- `context()`: the attributes of the `context` entity definition (name, control type, value type, required, selectables, embedded / foreign-key targets), via a port the testbed wires to base-entity (`base-rule` has no compile dependency on `base-entity`). Note: `context` is the definition `name`, not `code`.
- `validate()`:
  1. compile in `RuleEngine` (syntax);
  2. static check: every `entity.x` path exists in the context entity (semantics the compiler misses);
  3. dry run against 2-3 generated sample objects: must return a boolean and must not throw. The result value is not judged; it is reported back to the user as "true for sample A, false for sample B".

### 4.3 Calculation task: `calculation.source`

- `guide()`: the YAML shape, FEEL subset supported by feel-scala, row / group / single-valued decisions, decision tables, aggregates, ranking, outputs; examples from `docs/base-calculations` and seeded calculations.
- `context()`: input and output entity definitions with their attributes; existing calculations of the organization (names and outputs, for dependency order); units and currencies.
- `validate()`: `CalculationValidator` + `CalculationAnalyzer` (YAML parse, FEEL compile, references, dependency cycles); optional evaluate preview on generated inputs as for rules.

### 4.4 API (`ai-api.yaml`)

`POST /organizations/{orgKey}/ai/assist/{task}`

- Request: `text` (required), `parameters` (string map), `current` (optional).
- Response 200: `candidate`, `explanation`, `assumptions[]`, `issues[]`, `rounds`, `model`.
- 200 with ERROR issues = the model could not produce a valid candidate within the rounds; the UI shows the best candidate and its issues.
- 404 unknown task; 403 no access; 409 `assistant-not-configured` (no subscription); 429 quota exceeded. Reuse the existing assistant exception types and handler.
- `GET /organizations/{orgKey}/ai/assist`: the available tasks for the caller (empty when not configured). The frontend uses it to decide whether to render the action.

Synchronous to start with (a few seconds, at most 4 model calls). Switch to SSE only if latency proves a problem.

### 4.5 Frontend

- `BaseEntityAttrDescriptor.aiAssist?: { task: string; from: string; parameters?: string[] }`. `from` names the source attribute (`description`); `parameters` names the other attributes sent along (`context`).
- `base-entity-frontend` renders one icon button beside a field with `aiAssist` when the `AI_ASSIST_CLIENT` token reports the task available. The button is enabled once the description has text. A click writes the candidate into the field; the user still saves. There is no dialog and no diff. Undo is offered through a snackbar when the field held a value. Explanation and assumptions are returned by the API but not shown.
- `base-ai-frontend` provides the real `AI_ASSIST_CLIENT` (HTTP + availability cache); applications opt in with its providers.
- `base-rule.descriptors.ts`: `expressionAttr.aiAssist = { task: 'rule.expression', from: 'description', parameters: ['context', 'severity'] }`.
- `base-calculation-frontend`: the Source tab reuses the same `base-ai-assist-button`, enabled from the source's own `description:` key. It writes into the editor as an unsaved change.

## 5. Phases

### Phase 1: Port and loop

- `AuthoringAssistParticipant`, `AssistRequest` in `processpuzzle-core`.
- `ai.assist.AuthoringAssist`: participant registry, prompt assembly, structured output, validate/repair loop, quota, audit (task, org, principal, rounds, model, tokens; never the text itself, only its hash, as `ArgumentHash` does).
- Endpoint and DTOs in `ai-api.yaml`; `AiProperties.assist.enabled` (default off).
- Tests: loop with a fake `ChatModel` (valid first time; repaired on round 2; gives up after `maxRepairRounds`), unknown task, not configured, quota, audit content; `ModularityTests`.

### Phase 2: Rule task

- `RuleExpressionAssist` in `base-rule-backend` + `rule-expression-guide.md` classpath resource.
- Entity attribute lookup port (`RuleContextDirectory`) in `rule :: port`, adapter in the testbed composition root over base-entity.
- Static path check and sample-object dry run (sample generation: reuse the descriptor-to-value logic of the e2e fixture generator, ported to Java or simplified to type defaults).
- Tests per validation step; testbed `contextLoads`.

### Phase 3: Frontend for rules

- `aiAssist` descriptor property, the button and the `AI_ASSIST_CLIENT` token (no client, no button) in `base-entity-frontend`.
- Client and providers in `base-ai-frontend`; wire in the testbed.
- Rule descriptor update.
- Specs: button hidden when unavailable; disabled without a description; a click writes the control and marks it dirty without saving; issues and undo. Not built: an e2e test against a stubbed assist endpoint.

### Phase 4: Calculation task

- `CalculationSourceAssist` in `base-calculation-backend` + guide resource; context port for entity definitions if not already available to the module.
- Button on the Source tab (built as a button beside Validate and Save).
- Tests as for rules.

### Phase 5: Chat parity

- Built: `RuleAuthoringParticipant` and `CalculationAuthoringParticipant`, so the design assistant can list, read, save and check rule and calculation drafts. A calculation draft's JSON is `{"source": ...}`, keyed by the source's `calculation` id.
- A save is refused on ERROR-level findings from the same checks the assist uses (expression checks for rules, `SourceChecker` for calculations). Warnings pass. The form does not apply this bar.
- Not done: the guides from Phases 2 and 4 are not added to the design assistant's authoring guide, which the chat does not yet read.

### Phase 6: Evaluation set (not started)

- Per task, 20-30 pairs of (description, context, accepted artifact) as YAML under the feature's test resources; a tagged, manually run test that scores a real model: valid on first round, valid after repair, semantically equal to the expected artifact (same results on the sample objects). Run when changing the guide or the model.

### Later tasks on the same port

`rule.explain` (expression to description, for review), `rule.samples` (objects that pass / fail a rule), `entity.attributes` (attributes from prose), `rule.message` (user-facing message + translations), `calculation.explain`. Each is one participant in the owning feature, with no change to `ai.assist`.

## 6. Open points and risks

- **Semantic correctness.** Compiling and dry runs prove that the candidate is well formed and runs; they cannot prove that it means what the user meant. The explanation, assumptions and sample results exist so the user can judge. The evaluation set (Phase 6) measures how often the model gets it right.
- **Sample-object generation** in Java does not exist yet; type defaults may be too weak for range rules. Decide in Phase 2 whether to port the e2e generator.
- **Guide size vs. context window.** Long guides cost tokens on every call; keep them focused per task and measure token usage in the audit log.
- **Calculation context size.** An organization with many entities may produce a big context; restrict it to the input / output entities and their direct references.
- **Determinism.** Use a low temperature; the same description can still give different candidates. Acceptable, since the user reviews every candidate.

## 7. As built: deviations from this plan

- **Interaction:** one button, no dialog, diff or accept step (decided with the user on 2026-11-26).
- **One flag:** the assist is enabled by `processpuzzle.ai.assistant.enabled`; there is no separate `assist.enabled`.
- **Status codes:** "not configured" is 428, as the chat endpoint uses, not 409. Quota is 429 and an unavailable model 502.
- **Port:** `AuthoringAssistParticipant` has `checkAccess(orgKey)`, so a caller who may not edit the target is refused before any model call. The `ai` module cannot call `OrganizationGuard` itself.
- **Audit:** `AssistantAuditLog` gained an abstract `assist(...)` method. Any custom audit bean in another repository must implement it.
- **Parameters:** the `severity` parameter was dropped; `rule.expression` takes only `context`.
- **Rule checks:** a path check against the entity (the engine answers `undefined` for an unknown name, so this check is essential), then a dry run on three generated sample objects. A failure on the fully filled object is an error; a failure on the sparser ones is a warning.
- **Calculation checks:** the validator, plus that every named entity exists, that an embedded input's path is a list, and that every output attribute exists. The assist keeps the user's `calculation` id and `description`.
- **Model options:** no low temperature yet; it waits for the evaluation set, since reasoning models reject some options.
- **Verification:** unit and integration tests run in CI, with the model scripted. Neither button has been checked in a running browser, and there is no end-to-end test.

