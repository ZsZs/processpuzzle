package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.workflow.definition.domain.AuthType;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationPort;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationResult;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls a {@link ToolDefinition}'s operation over HTTP using Spring's {@code RestClient}.
 *
 * <p><b>Payload templating:</b> {@code operation.payloadTemplate} supports {@code ${varName}}
 * placeholders, substituted from {@code resolvedPayload} via simple string replacement — not a
 * real PPCL evaluation (base-workflow doesn't own the PPCL engine; that lives in
 * {@code processpuzzle-core}/base-rule's GraalJS engine per the ProcessPuzzle architecture notes).
 * Revisit once a shared PPCL evaluation entry point is exposed to feature modules.
 *
 * <p><b>Secrets:</b> {@code ToolAuthConfig.secretRef} is resolved from {@link Environment} — i.e.
 * application properties / environment variables — not a dedicated secret store. Swap this for a
 * Vault-backed resolver if/when processpuzzle-backend adopts one; the {@link ToolInvocationPort}
 * boundary means that swap never touches the use case layer.
 *
 * <p><b>Unverified against a real build:</b> the exact generic signature of
 * {@code RestClient.RequestHeadersSpec#exchange(ExchangeFunction)} (specifically the response
 * parameter's fully-qualified nested type name) varies slightly across Spring Framework 6.x point
 * releases and could not be checked here — no Maven/network access to compile against the actual
 * spring-web version this repo pins. If this file fails to compile, the fix is almost certainly
 * just correcting that one nested type reference; the request-building and status/JSON handling
 * logic around it is unaffected.
 */
@Component
public class RestToolInvocationAdapter implements ToolInvocationPort {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+)}");
    private static final List<Integer> DEFAULT_EXPECTED_STATUS_CODES = List.of(200, 201, 204);

    private final RestClient.Builder restClientBuilder;
    private final Environment environment;

    @Autowired
    public RestToolInvocationAdapter(ObjectProvider<RestClient.Builder> restClientBuilderProvider, Environment environment) {
        this.restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        this.environment = environment;
    }

    public RestToolInvocationAdapter(RestClient.Builder restClientBuilder, Environment environment) {
        this.restClientBuilder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.environment = environment;
    }

    @Override
    public ToolInvocationResult invoke(ToolDefinition tool, ToolOperation operation, Map<String, Object> resolvedPayload) {
        RestClient client = restClientBuilder.baseUrl(tool.getBaseUrl()).build();
        String body = resolvePlaceholders(operation.getPayloadTemplate(), resolvedPayload);

        try {
            var requestSpec = client.method(toSpringHttpMethod(operation.getMethod()))
                    .uri(operation.getPath())
                    .headers(headers -> applyAuth(headers, tool));

            if (body != null && !body.isBlank()) {
                requestSpec.contentType(MediaType.APPLICATION_JSON).body(body);
            }

            return requestSpec.exchange((request, response) -> {
                int statusCode = response.getStatusCode().value();
                boolean success = isExpected(statusCode, operation.getExpectedStatusCodes());
                Map<String, Object> responseBody = success ? readJsonSafely(response) : null;
                String error = success ? null : "Tool returned unexpected status " + statusCode;
                return new ToolInvocationResult(success, statusCode, responseBody, error);
            });
        } catch (RuntimeException ex) {
            return new ToolInvocationResult(false, 0, null, "Tool call failed: " + ex.getMessage());
        }
    }

    private void applyAuth(HttpHeaders headers, ToolDefinition tool) {
        var auth = tool.getAuth();
        if (auth == null || auth.getType() == null || auth.getType() == AuthType.NONE) {
            return;
        }
        String secret = auth.getSecretRef() == null ? null : environment.getProperty(auth.getSecretRef());
        if (secret == null) {
            return; // misconfiguration surfaces as an auth failure from the tool itself, not silently here
        }
        switch (auth.getType()) {
            case BEARER_TOKEN -> headers.setBearerAuth(secret);
            case API_KEY -> headers.set("X-Api-Key", secret);
            case BASIC -> {
                // secretRef is expected to resolve to "username:password" for BASIC auth.
                String[] parts = secret.split(":", 2);
                if (parts.length == 2) {
                    headers.setBasicAuth(parts[0], parts[1]);
                }
            }
            case NONE -> {
                // nothing to do
            }
        }
    }

    private static org.springframework.http.HttpMethod toSpringHttpMethod(com.processpuzzle.workflow.definition.domain.HttpMethod method) {
        return org.springframework.http.HttpMethod.valueOf(method.name());
    }

    private static boolean isExpected(int statusCode, List<Integer> expected) {
        List<Integer> effective = (expected == null || expected.isEmpty()) ? DEFAULT_EXPECTED_STATUS_CODES : expected;
        return effective.contains(statusCode);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonSafely(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(Map.class);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static String resolvePlaceholders(String template, Map<String, Object> values) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = values == null ? null : values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "null" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
