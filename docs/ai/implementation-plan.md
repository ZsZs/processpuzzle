# AI Definition Generator: Implementation Plan

Status: plan, 2026-10-26. Implements [ai-definition-generator-v2.md](ai-definition-generator-v2.md); where this plan deviates, it says so.

## Decisions

| Topic | Decision |
|---|---|
| Module | Existing `base-ai-backend` (Modulith module `ai`, new package `assistant`) and `base-ai-frontend` |
| Roles | New realm role `definition-designer`; AI draft rights = token scope `definitions:draft` ∩ user holds `definition-designer` |
| LLM vendor | Free and EU-hosted for now: **Mistral** (Paris) free API mode, alias `pp-designer`; Scaleway (France) is the documented alternative. Customers choose a paid provider by editing the gateway config. Behind a **LiteLLM** gateway. Spring AI's OpenAI-compatible client points at LiteLLM (`base-url`), so the model/vendor is a LiteLLM config matter, not a code one |
| Role enforcement | `definition-designer` is checked **only on the assistant's tools** (chat and MCP). `OrganizationGuard.requireDesign` is unchanged, so existing designers are not locked out |
| LiteLLM | One **shared infrastructure** service (`docker-compose-infrastructure.yaml`), reached on the internal network only. Not wired into the Testbed; only custom application stacks enable the assistant |
| Tool execution | In-process, never HTTP, never token passthrough |
| Publish | Never exposed. Review and publish stay in the existing release panel (`base-release`) |
| Tool schemas | Hand-defined per participant (resolved from OpenAPI component schemas where they exist); no `x-mcp` generator in the first slice |

## Findings that shape the plan

- `ai` module may depend only on `core` and `shared` (`package-info.java`). It cannot call entity/state/workflow/app use cases, so features implement a **port declared in `core`** and the testbed (or each feature) supplies the adapters, the same shape as `DefinitionImportParticipant` / `ReleaseParticipant`.
- Draft/release is already modelled: `Channel.DRAFT|RELEASED` rows for entity/state/workflow; app keeps draft and `publishedGraph` in one row. Only app has a validate endpoint; the rest validate on write and in `ReleaseParticipant.check(...)`.
- Authorization is `OrganizationGuard.requireAccess/requireDesign(orgKey)`; `requireDesign` has no designer role yet. The org comes from the path / token realm.
- No Spring AI / MCP anywhere yet. `docs/ai/*.java|ts` are prototypes (wrong package `org.processpuzzle.ai`, YAML-returning `propose_*` tools) to be reworked, not copied.
- Jackson: Boot 4.1 and Spring AI 2.0 / MCP SDK 2.0 use Jackson 3 (`tools.jackson`); the repo has 52 files on Jackson 2 `databind` and 5 on Jackson 3. Mixed use must be checked in Phase 0.

## Phase 0: Spikes and groundwork (started)

Goal: remove the unknowns before any feature code.

1. **Version matrix** (researched 2026-10-26): Spring AI 2.0.x (GA 2026-06-12, 2.0.1 built on Boot 4.1.1) fits our Boot 4.1.0; 2.1.0-M1 moves to Boot 4.2, so stay on 2.0.x. `mcp-security` 0.1.x targets Spring AI 2.0.x (latest 0.1.14); 0.0.x is for 1.1.x. Local `~/.m2` only has `spring-ai-bom:1.1.2`, so 2.0.x must be resolved from Central.
2. **Compile spike** in a scratch branch/module: add `spring-ai-bom` 2.0.x to the parent `dependencyManagement`; add `spring-ai-starter-model-openai` to `base-ai-backend`; build the testbed backend and run `contextLoads` + `ModularityTests`. Watch for: Jackson 2/3 conflicts, extra auto-configuration activating without an API key (must be inert when `processpuzzle.ai.assistant.enabled=false`), fat-jar and memory growth (stage host is memory-constrained, see README on the Modulith actuator removal).
3. **LiteLLM**: add a `litellm` service to `tools/docker/` (compose + env per environment) with one model alias (e.g. `pp-designer`); smoke-test a Spring AI `ChatClient` call with a tool call through it.
4. **Keycloak**: realm role `definition-designer` added to all four realm imports (done). Client scopes `mcp:tools` and `definitions:draft` (with audience mapper) follow in Phase 5. `requireDesign` stays as it is; the role is enforced in the assistant only.
5. **Exit criteria**: a throwaway test that sends one prompt through Spring AI -> LiteLLM -> model and executes one `@Tool` method; testbed context and Modulith tests green; written findings appended below.

Deliverable: a short "Phase 0 findings" section here and an ADR if the versions or the Jackson situation force a change.

## Phase 1: Authoring port in `processpuzzle-core`

- `DefinitionAuthoringParticipant` in `core/definition`: `kind()`, `schema()` (JSON Schema), `list(org)`, `get(org, key)`, `saveDraft(org, key, json)` (upsert by key, idempotent, writes DRAFT channel only), `validate(org, key)` returning issues (ERROR/WARNING, path, text).
- Cross-kind `validate_drafts` delegates to `ReleaseParticipant.check` for reference problems.
- Implement for **entity** and **state** first, inside their own libs, calling the existing create/replace use cases (same validators, same `OrganizationGuard`).
- Tests: per participant, idempotent re-save, invalid input returns issues, cannot touch RELEASED rows, other-tenant key rejected.

## Phase 2: Assistant backend (`ai.assistant`)

- Port `DesignAssistant` (conversation-level: message in, stream of events out), adapter on Spring AI `ChatClient` -> LiteLLM. Replaces v2's `LlmClient.complete(...)`.
- Tools built from the collected `DefinitionAuthoringParticipant`s: `list_definitions`, `get_definition`, `get_schema`, `save_<kind>_draft`, `validate_drafts`, `ask_clarification`, `get_authoring_guide`. Organization from the security context only.
- Session persistence (tenant-scoped, JPA, Flyway-style migration as the module does today), SSE chat endpoint and DTOs in `ai-api.yaml`; events: token, clarification, draft-updated, issues, explanation, done, error.
- Controls: max tool calls per run, max 3 repair rounds, per-org quota and token usage records, audit log (principal, org, tool, argument hash, model, usage; never tokens or secrets), `definition-designer` required, tool results and customer text treated as untrusted.
- Config under `processpuzzle.ai.assistant.*` (enabled flag default off, LiteLLM base URL and key, model alias). Add to `AiProperties`.
- Tests: tool loop with a fake `ChatModel`, authorization, caps, audit; Modularity test; testbed `contextLoads`.

## Phase 3: Authoring guide

- `docs/ai/authoring-guide.md` -> copied as a classpath resource: ground rules, step order, validation/repair rules, Business Starter as worked example; fill the schema TODOs from the real definitions.
- One source feeds the system prompt, `get_authoring_guide` tool and later the MCP resource.

## Phase 4: Chat UI (`base-ai-frontend`)

- Rework `docs/ai/chat.*.ts` and `assistant-api.service.ts` to the repo's NgRx Signals / Transloco / `*.facade.ts` conventions; i18n in the five existing locales.
- **Hosting:** the chat is a framework component (`AssistantPanel`) exported from `base-ai-frontend`, not a page of its own. The Design tools (ProcessPuzzle UI / Admin, in the private `processpuzzle-biz` repo) embed it as a side panel next to the entity, state, workflow and app designers, passing the designer they are in as context (open definition kind and key). When the assistant saves a draft, the panel asks that designer to reload the definition. A standalone route stays available for custom applications that have no designer.
- Chat panel with clarification options, issues list, draft links into the existing designers, and a link to the release panel. No publish button.
- Route and provider wiring like `base-ai.routes.ts` / `base-ai.providers.ts`; shown only to `definition-designer`.

## Phase 5: MCP server

- `spring-ai-starter-mcp-server-webmvc`, `protocol=STATELESS` (bearer per request, multi-instance) at `/mcp`, same tools through `@McpTool`.
- `mcp-server-security-spring-boot` 0.1.x, separate filter chain for `/mcp/**` and `/.well-known/**`, audience validator, issuer pinned with `jwk-set-uri` for the internal Keycloak address, per-user rate limit, protected-resource metadata (RFC 9728).
- Verify the whole chain in MCP Inspector, then Claude Desktop with a pre-registered public PKCE client. Dynamic client registration stays off.
- Check how `/mcp` fits the existing `TenantAuthenticationManagerResolver` (one issuer per realm) before choosing the security-chain layout.

## Phase 6: Evaluation set

- 10-20 workflow descriptions with expected definitions as YAML (reuse starter bundles as references); a harness that runs them against the assistant and diffs the result. Run manually or nightly, not in the PR build (cost, nondeterminism). Record model alias and guide hash per run.

## Phase 7: Extend to workflow and app

- Participants for workflow (+ role, task, artifact, tool) and app (draft graph; validation through `ValidateAppDefinition`); update the guide and evaluation set.

## Delivery

- Branch `feature/base-ai/definition-generator`; one PR per phase where possible.
- Release one library at a time (memory: release one at a time), order: `processpuzzle-core` -> `api-contracts` -> entity / state -> `base-ai-backend` -> `base-ai-frontend`; Java libs to GitHub Packages only.
- Before each PR: `bash tools/scripts/pre-push-check.sh`, testbed `contextLoads` and `ModularityTests`; Maven builds never in parallel.
- Docs: update `base-ai-backend` README (it currently says recognition is its whole job), root README feature-maturity table and `docs/application-stacks.md` for LiteLLM.

## Open risks

- Free-tier LLM: Mistral free mode may train on inputs unless opted out, publishes no limits, and may change (its pricing page changed in October 2026). Demo data only; the evaluation harness must handle 429s. Small or free models may call tools unreliably: measure in Phase 6 before promising anything.

- Jackson 2/3 mix may break Spring AI tool schema generation or MCP serialization.
- `requireDesign` change affects every existing designer user.
- Backend memory/boot-time cost on the stage host.
- Model cost and abuse: quotas must exist before the assistant is enabled outside dev.
- `mcp-security` is community-maintained and not yet endorsed by Spring AI.

## Phase 0 findings

Done 2026-10-26 (steps 1-2):

- **Spring AI 2.0.2** (BOM) with `spring-ai-starter-model-openai` resolves against Boot 4.1.0. Jackson 2 (yaml dataformat, databind 2.21) and Jackson 3 (3.1.4, pulled by Boot and Spring AI) coexist on the classpath; no clash so far.
- `base-ai-backend` tests (185) and the testbed `ModularityTests` and `ProcessPuzzleTestbedBackendApplicationTests` pass.
- **The starter is not inert.** It auto-configures six OpenAI models and fails context startup without an API key. Fixed in the testbed `application.yaml`: `spring.ai.model.{embedding,image,moderation,audio.*}=none`, and `chat` is `${SPRING_AI_MODEL_CHAT:none}`. Every application that embeds `base-ai-backend` must set the same, so Phase 2 should either ship these defaults from the library or drop the starter for `spring-ai-client-chat` plus a hand-built `ChatModel` behind `processpuzzle.ai.assistant.enabled`.
- Added (2026-10-26): `definition-designer` in the four realm imports; `litellm` service (`tools/docker/litellm/` Dockerfile and `config.yaml`, alias `pp-designer`) in `docker-compose-infrastructure.yaml`, the build overlay and the three infrastructure env files (`LITELLM_MASTER_KEY`, `MISTRAL_API_KEY`). Compose config validates.
- Not yet done: the `processpuzzle-litellm` image is in the build and deploy workflow matrices and the promotion loop, but unproven until CI runs; the two secrets in Coolify; the tool-call smoke test (needs a real key).
