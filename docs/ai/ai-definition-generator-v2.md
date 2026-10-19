# AI Definition Generator for ProcessPuzzle: Design (v2)

Status: design sketch, updated 2026-10-18. This version merges the original sketch with the findings on tool access (OpenAPI to tools), the MCP server, the authoring guide and Keycloak authentication.
Companion file: `authoring-guide.md` (the text the model reads before authoring).

## 1. Goal

A customer describes in plain text which workflow they want to automate. AI generates Entity, State, Workflow and App definitions as **drafts** inside ProcessPuzzle, using the platform's own services. A human reviews and publishes.

Changes from v1: the AI acts through the platform's DRAFT/PUBLISH system (tool calls) instead of returning YAML to be imported. YAML stays as an export/import path and as the format of the evaluation set.

## 2. Architecture

Put the LLM behind an outbound port in the hexagonal backend, in its own Spring Modulith module (e.g. `base-ai-backend`). Feature code never calls a vendor directly.

- **LLM port and adapter:** the adapter uses Spring AI (`ChatClient`, structured output, tool calling). A gateway such as LiteLLM or Portkey can sit in front for multi-vendor routing, budgets and key management.
- **Tools port (new):** the platform exposes its draft-authoring operations as MCP tools through an inbound adapter (section 4). The embedded chat and external AI clients use the same tool set.
- **Tenant control:** per-customer quotas, cost tracking, audit logging of prompts, tool calls and outputs. API keys stay server-side only.
- **Vendor strategy:** start with one strong model via API, keep the port so vendors can be switched or an EU-hosted / self-hosted option offered later (data residency).

```
Angular chat UI --> backend chat endpoint (user token)
                       |  LLM loop (Spring AI ChatClient)
                       v
                 MCP tools (in-process or MCP client)  <--- external MCP clients (Claude Desktop, IDEs)
                       |
                       v
             use cases / draft services (same validators, same authorization as REST)
                       |
                       v
                 DRAFT store  --(human review)-->  PUBLISH
```

## 3. How the model learns the API (not a one-time action)

The model has no memory between calls. The API description is sent with every request as **tool definitions** (name, description, JSON Schema for input). What is one-time is the work of preparing those definitions.

- Expose a **curated subset**: about 10-20 draft-authoring operations (create/update draft, validate, list existing definitions, get schema, diff). Fewer, well-described tools outperform a large API surface.
- **`publish` is never exposed.** The AI can only write drafts, so a human always confirms. This is the main benefit of the existing DRAFT/PUBLISH system.
- Tools run with the caller's identity and the same validators and authorization as a human user.

### 3.1 OpenAPI operation to tool

| OpenAPI | MCP tool |
|---|---|
| `operationId` | tool `name` |
| `summary` + `description` | tool `description` (drives tool selection; write it from the model's point of view) |
| path/query params + request body schema | one flat `inputSchema` (params as properties, body as `body`) |
| response / error body | tool result text, `isError` set on 4xx/5xx; return validation errors verbatim so the model can repair |

Mark exposed operations with a vendor extension, for example `x-mcp: true`:

```yaml
paths:
  /api/entity-definitions/drafts:
    post:
      operationId: create_entity_draft
      x-mcp: true
      summary: Create a new DRAFT of an entity definition.
      description: >
        Creates a draft only; it is not live until a human publishes it.
        Returns the draft id and validation issues.
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/EntityDefinition' }
```

Generator sketch (swagger-parser plus MCP Java SDK; `Tool` / `CallToolResult` constructors or builders vary by SDK version, so check the version Spring AI pulls in):

```java
@Component
class OpenApiToolFactory {
  private final ObjectMapper json = Json.mapper();

  List<McpServerFeatures.SyncToolSpecification> build(String specLocation) {
    ParseOptions opts = new ParseOptions();
    opts.setResolve(true);
    opts.setResolveFully(true);            // inline $refs; breaks on recursive schemas
    OpenAPI api = new OpenAPIV3Parser().read(specLocation, null, opts);

    var tools = new ArrayList<McpServerFeatures.SyncToolSpecification>();
    api.getPaths().forEach((path, item) ->
        item.readOperationsMap().forEach((method, op) -> {
          if (isExposed(op)) tools.add(toTool(path, method, op));
        }));
    return tools;
  }

  private boolean isExposed(Operation op) {
    return op.getExtensions() != null
        && Boolean.TRUE.equals(op.getExtensions().get("x-mcp"));
  }

  // toTool(): build one flat input schema from params + body, create the Tool,
  // and a handler that executes the operation (see 3.2 for how the handler executes).
}
```

### 3.2 Two ways to execute a tool

1. **In-process adapter (recommended).** The tool handler calls the application-layer use case directly, with the authenticated principal in the security context. No HTTP hop, no token forwarding, and the same `@PreAuthorize` rules as the REST controllers. Hand-written tools use Spring AI annotations and get their input schema generated from the Java types:

```java
@McpTool(name = "create_entity_draft",
         description = "Create a DRAFT entity definition. Never publishes.")
public DraftRef createEntityDraft(
    @McpToolParam(description = "The entity definition") EntityDefinition definition) {
  return createEntityDraftUseCase.execute(definition);
}
```

2. **Generated proxy to REST.** Generated tools call the REST API over HTTP. Do not forward the incoming token (see section 6.3); use token exchange or an in-process call instead.

Suggested mix: generate the tool input schemas from OpenAPI, execute in-process, and hand-write individual tools where the generated ones perform poorly.

## 4. MCP server in Spring

- **Dependency:** `spring-ai-starter-mcp-server-webmvc`.
- **Config:** `spring.ai.mcp.server.protocol=STREAMABLE` publishes the Streamable HTTP endpoint at `/mcp`. Set it explicitly; depending on the version the starter may fall back to the older SSE transport and `/mcp` returns 404.
- **Primitives:** tools (`@McpTool`), resources (`@McpResource`), prompts (`@McpPrompt`). Tools are the core. Resources hold read-only context such as the definition JSON Schemas and the authoring guide.
- **Testing:** MCP Inspector (`npx @modelcontextprotocol/inspector http://localhost:8080/mcp`, transport Streamable HTTP).
- **Option:** the `STATELESS` protocol fits bearer-token-per-request auth and multi-instance deployments.

## 5. Authoring guide

`authoring-guide.md` is the text the model reads before creating anything: ground rules (drafts only, ask when ambiguous, reuse existing definitions), the step order, validation and repair rules, and a worked example. Fill its TODO lines from the real schemas and use a Business Starter as the worked example.

Exposing it reliably (many MCP clients do not read resources automatically):

1. Expose it as a resource (`processpuzzle://guide/authoring`, text/markdown).
2. Add a `get_authoring_guide` tool returning the same text, and reference it in the description of every create-draft tool.
3. If the Spring AI version supports MCP server instructions, put a short summary of the ground rules there.
4. In the embedded chat, inject the guide into the system prompt directly.

All of these come from the one source file.

## 6. Authentication and authorization (Keycloak)

### 6.1 Roles and flows

Keycloak is the authorization server; the MCP server is an OAuth resource server.

| Caller | How it gets a token |
|---|---|
| External MCP client (Claude Desktop, VS Code, customer tools) | MCP OAuth flow: 401 with metadata pointer, discovery, client registration, user login, PKCE |
| Embedded chat | The user is already logged in; the backend uses that user's token (same app) or exchanges it |

External flow: the MCP server answers 401 and points to its Protected Resource Metadata document (RFC 9728, served by the MCP server, not Keycloak); the client finds Keycloak, reads its metadata, then uses a pre-registered client or Dynamic Client Registration.

### 6.2 Spring side

Use the community module `spring-ai-community/mcp-security` (resource server support; its 0.0.x versions target Spring AI 1.1.x, 0.1.x targets 2.0.x). Boot auto-configuration artifact: `mcp-server-security-spring-boot`.

```properties
spring.ai.mcp.server.protocol=STREAMABLE
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://<keycloak-host>/realms/<realm>
```

```java
@Configuration
@EnableWebSecurity
class McpSecurityConfig {
  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer;

  @Bean
  SecurityFilterChain mcpChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/mcp/**", "/.well-known/**")
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .with(McpServerOAuth2Configurer.mcpServerOAuth2(),
              c -> c.authorizationServer(issuer))
        .build();
  }
}
```

Class and method names are from the module README; verify against the version in use. Add an audience validator: Spring's JWT decoder checks issuer and expiry by default, so require the MCP resource URI in `aud` explicitly.

### 6.3 No token passthrough

The MCP authorization guidance advises that a server accepts only tokens issued for itself and does not forward them to other APIs. Therefore: execute tools in-process (preferred), or exchange the incoming token at Keycloak (RFC 8693) for a short-lived token with the REST API audience and only the draft scopes. Check which token-exchange variant the Keycloak version supports.

### 6.4 Keycloak setup

1. Client scopes: optional `mcp:tools` and a narrow `definitions:draft`.
2. Audience mapper on those scopes so `aud` contains the MCP resource URI. The RFC 8707 `resource` parameter is experimental and off by default in Keycloak; do not rely on it.
3. External clients: public clients, PKCE, loopback redirect URIs; pre-register the supported client apps. Confidential clients do not fit desktop MCP clients.
4. Dynamic Client Registration: supported, but default policies restrict anonymous registration (Trusted Hosts policy). Open DCR on production is a risk; prefer pre-registration and enable DCR only if needed.
5. Embedded chat: the existing Angular client and user token suffice when the MCP server runs in the same backend.

### 6.5 Authorization model: the AI gets less than the user

A tool call is allowed only if the token has the **scope** `definitions:draft` (what this client may do for the user) **and** the user holds the **role** that permits drafting (what the user may do). The AI's effective rights are the intersection. `definitions:publish` is never part of the MCP scopes, even for users who can publish. Take the tenant from the token (claim, or realm), never from a tool argument.

### 6.6 Operational points

- Issuer mismatch (public hostname in tokens vs internal Keycloak URL) is the most common failure: pin the issuer, use `jwk-set-uri` for the internal address.
- Long generation runs can outlive a short access token: allow refresh, and fail cleanly with 401 rather than half-applying a change.
- Per-tenant and per-user rate limits on `/mcp`; audit log with principal, tenant, tool name and argument hash; never log tokens.
- Verify the whole chain in the MCP Inspector before connecting a real client.

## 7. Pipeline

Each step's result is validated before the next step runs. Steps now make tool calls that create or update drafts.

1. **Analyze:** extract actors, data objects, steps and rules from the free text. If something is ambiguous, return clarifying questions instead of guessing.
2. **Entities:** create entity drafts against the entity JSON Schema.
3. **States:** one state machine per entity that needs a lifecycle (states, transitions, guards), referencing entities by ID.
4. **Workflow:** activities, roles and tasks in the SPEM structure, referencing entity and state IDs.
5. **App:** routes, pages and widgets, bound to the entities and workflows from the earlier steps.
6. **Validate and repair:** call the validate tool; validation issues come back as tool results; feed them to the model, max 2-3 retries, then stop and report.
7. **Review:** show a diff or preview of the drafts to the customer; a human publishes.

### Updated ports

```java
// inbound, used by the API / UI
public interface DefinitionGenerator {
  GenerationResult generate(GenerationRequest req);
}

record GenerationRequest(String tenantId, String description,
    Locale locale, Set<ArtifactType> targets) {}

// results are draft references, not YAML text
record GenerationResult(List<DraftRef> drafts,
    List<ValidationIssue> issues, List<ClarifyingQuestion> questions, TokenUsage usage) {}

// outbound, implemented by the Spring AI / gateway adapter
public interface LlmClient {
  <T> T complete(PromptSpec prompt, JsonSchema schema, Class<T> type);
}
```

## 8. Making the output reliable

- Generate JSON Schemas from the existing definitions and use them both as tool input schemas and as the structured-output contract.
- Validate every result with the same validators the platform uses.
- The AI never publishes; everything goes through reviewed drafts.
- Treat tool results and customer data as untrusted input (names and descriptions may contain instructions); no tool does more than drafting.
- Tool pitfalls: fully resolved schemas can get huge and recursive ones break `setResolveFully`; make operations idempotent (upsert by draft id) because models retry; cap tool calls per run and per tenant.

## 9. Design notes

- Pass earlier results as compact context (IDs and names) to keep prompts small and stable.
- Log prompt, model version, tool calls and output per run to make results reproducible.
- Build an evaluation set early: 10-20 sample workflow descriptions with expected definitions (YAML makes the diff easy), so prompt, guide or model changes can be measured.
- Start with one strong model; MCP keeps the tool side vendor-independent.

## 10. First slice

1. Pick the draft operations needed to create one entity plus one state machine; tag them `x-mcp: true`.
2. Generate the tool input schemas from OpenAPI; implement execution in-process.
3. Add the starter, the security configuration and the Keycloak scopes; verify in the MCP Inspector.
4. Write `authoring-guide.md` (fill the TODOs, add a Business Starter as the worked example) and expose it as a resource, tool and system prompt text.
5. Connect the embedded chat and run a few sample workflow descriptions from the evaluation set.
6. Then extend to workflow and app definitions.

## Sources

- https://code.visualstudio.com/learn/java-spring-boot/3-expose-tools-with-mcp
- https://thetalkingapp.medium.com/spring-ai-recipe-streamable-http-mcp-server-e9c70590b872
- https://www.codecentric.de/en/knowledge-hub/blog/building-mcp-servers-spring-ai
- https://java2ai.com/integration/mcps/mcp-overview
- https://github.com/infusionsoft/openapi-mcp-generator
- https://www.speakeasy.com/docs/ai-control-plane/mcp-gateway/building-servers/openapi
- https://modelcontextprotocol.io/docs/latest/tutorials/security/authorization
- https://phasetwo.io/tutorials/emerging/mcp-server-auth/
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html
- https://github.com/spring-ai-community/mcp-security
- https://spring.io/blog/2025/09/30/spring-ai-mcp-server-security/
- https://docs.activeviam.com/atoti-intelligence/6.1/developer-guide/mcp-server/setup/configure-oauth2-discovery.md
