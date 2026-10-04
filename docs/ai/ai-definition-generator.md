# AI Definition Generator for ProcessPuzzle: Design Sketch

## Goal

A customer describes in plain text which workflow they want to automate. AI generates the Entity, State, Workflow and App definitions, delivered via API or as YAML files.

## Architecture

Put the LLM behind an outbound port in the hexagonal backend, in its own Spring Modulith module (e.g. `base-ai-backend`). Feature code never calls a vendor directly.

- **Port and adapter:** the adapter uses Spring AI (`ChatClient`, structured output, tool calling). A gateway such as LiteLLM or Portkey can be added in front for multi-vendor routing, budgets and key management.
- **Tenant control:** per-customer quotas, cost tracking, audit logging of prompts and outputs. API keys stay server-side only.
- **Vendor strategy:** start with one strong model via API, keep the port so you can switch vendors or offer an EU-hosted / self-hosted option later (data residency).

## Ports

```java
// inbound, used by the API / UI
public interface DefinitionGenerator {
  GenerationResult generate(GenerationRequest req);
}

record GenerationRequest(String tenantId, String description,
    Locale locale, Set<ArtifactType> targets) {}

record GenerationResult(Map<ArtifactType, String> yamlDrafts,
    List<ValidationIssue> issues, TokenUsage usage) {}

// outbound, implemented by the Spring AI / gateway adapter
public interface LlmClient {
  <T> T complete(PromptSpec prompt, JsonSchema schema, Class<T> type);
}
```

## Pipeline

Each step's output is validated before the next step runs.

1. **Analyze:** extract actors, data objects, steps and rules from the free text. If something is ambiguous, return clarifying questions instead of guessing.
2. **Entities:** generate entity definitions against the entity JSON Schema.
3. **States:** one state machine per entity that needs a lifecycle (states, transitions, guards), referencing entities by ID.
4. **Workflow:** activities, roles and tasks in the SPEM structure, referencing entity and state IDs.
5. **App:** routes, pages and widgets, bound to the entities and workflows from the earlier steps.
6. **Cross-check and repair:** run the platform's own validators plus reference checks (no dangling IDs). Feed errors back to the model, max 2-3 retries.
7. **Draft:** store as a versioned draft, show a diff or preview to the customer, import only after confirmation.

## Making the output reliable

- Generate JSON Schemas from the existing definitions and use them as the structured-output contract.
- Validate every result with the same validators the platform uses.
- Never apply results directly; always go through a reviewed draft (the YAML import path is the natural landing zone).

## Design notes

- Pass earlier results as compact context (IDs and names) to keep prompts small and stable.
- Log prompt, model version and output per run to make results reproducible.
- Build an evaluation set early: 10-20 sample workflow descriptions with expected definitions, so prompt or model changes can be measured.

## Next step

Map the existing definition schemas to structured-output schemas (entity, state, workflow, app).
