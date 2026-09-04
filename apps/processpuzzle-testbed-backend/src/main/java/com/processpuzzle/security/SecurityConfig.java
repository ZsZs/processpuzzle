package com.processpuzzle.security;

import com.processpuzzle.core.tenancy.KnownRealms;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * The resource server. Validates a bearer token against <em>the realm that issued it</em>, which for
 * this platform means one issuer per organization plus the fixed platform realm.
 *
 * <p>{@link JwtIssuerAuthenticationManagerResolver} is what makes that possible without a static
 * issuer list: it reads the token's {@code iss} claim and asks
 * {@link TenantAuthenticationManagerResolver} which realm's keys to verify against. The set of
 * trusted issuers is therefore one configured realm plus whatever {@link KnownRealms} vouches for;
 * where an adapter for that port exists the set grows the moment a tenant is provisioned rather than
 * at the next restart. <b>This deployment wires no adapter</b> — it hosts a single realm, so
 * configuration is the whole answer and no tenant registry sits on the authentication path.
 *
 * <h2>What is closed, and what is not</h2>
 *
 * <ul>
 *   <li>{@code /platform/**} — the {@code platform-admin} authority, always. This is the surface that
 *       deletes tenants.
 *   <li>The org-admin user-management paths — authentication, always. These manage credentials.
 *   <li>Everything else — open unless {@code processpuzzle.security.require-authentication} is set.
 * </ul>
 *
 * <p>That last line is a deliberate, temporary compromise and the one thing to read before deploying:
 * the Angular applications do not send a bearer token yet, so closing the tenant API today would
 * answer 401 to every existing screen. Tenant isolation <em>is</em> enforced whenever a token is
 * present — see {@link JwtOrganizationAccessPolicy} — so the flag is the difference between "a token
 * cannot cross tenants" and "you must present a token". Any internet-facing deployment needs both;
 * see {@link SecurityProperties}.
 *
 * <p>Stateless, and CSRF disabled: there is no session and no cookie to forge a request with — the
 * credential is a bearer token the browser has to attach deliberately. CORS is not re-declared here:
 * {@code .cors(withDefaults())} applies the {@code CorsConfigurationSource} bean {@link
 * com.processpuzzle.CorsConfig} publishes, so there is still one place that decides which origins may
 * call this backend. It has to run inside <em>this</em> chain rather than as a filter around it,
 * because the responses that most need CORS headers are the 401 and 403 this chain writes itself.
 *
 * <p>Those two responses carry a body, which they did not used to: see
 * {@link ApiSecurityErrorHandler} for the shape and for why it is registered twice.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Paths that manage a tenant's users and roles — new, so nothing depends on them being open.
     *
     * <p>All under {@code /admin} because base-workflow already owns
     * {@code /organizations/{orgKey}/roles} for workflow role definitions; see org-admin-api.yaml.
     * That namespace is also what makes this list safe to state as a prefix: nothing else mounts
     * there, so it cannot accidentally close an unrelated endpoint.
     */
    private static final String[] ORG_ADMIN_PATHS = {
            "/api/organizations/*/admin/**",
            "/organizations/*/admin/**",
    };

    /** Operational and documentation endpoints, deliberately reachable without a token. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/api-docs", "/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
    };

    private final SecurityProperties properties;

    public SecurityConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * {@code getIfUnique} rather than {@code @ConditionalOnMissingBean}, for the reason
     * {@link com.processpuzzle.core.tenancy.OrganizationGuard} documents at length: that condition is
     * only reliable inside auto-configuration, and this package is component-scanned.
     */
    @Bean
    public AuthenticationManagerResolver<jakarta.servlet.http.HttpServletRequest> tenantAuthenticationManagerResolver(
            ObjectProvider<KnownRealms> knownRealms) {
        return new JwtIssuerAuthenticationManagerResolver(
                new TenantAuthenticationManagerResolver(
                        properties, knownRealms.getIfUnique(() -> KnownRealms.NONE)));
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationManagerResolver<jakarta.servlet.http.HttpServletRequest> resolver,
            ObjectMapper json) throws Exception {

        if (!properties.isRequireAuthentication()) {
            LOG.warn("processpuzzle.security.require-authentication is false: the tenant API is "
                    + "reachable without a bearer token. Tenant isolation is still enforced for "
                    + "requests that carry one, and /platform/** plus the org-admin paths always "
                    + "require authentication. Set it to true for any internet-facing deployment.");
        }

        ApiSecurityErrorHandler errors = new ApiSecurityErrorHandler(json);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> {
                    // Preflight carries no Authorization header by definition, so it must never be
                    // challenged: a 401 here reaches the browser as an opaque CORS failure and the
                    // frontend sees HTTP status 0 with nothing to explain it.
                    requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    requests.requestMatchers(PUBLIC_PATHS).permitAll();
                    requests.requestMatchers("/api/platform/**", "/platform/**")
                            .hasAuthority(properties.getPlatformAdminAuthority());
                    requests.requestMatchers(ORG_ADMIN_PATHS).authenticated();
                    if (properties.isRequireAuthentication()) {
                        requests.anyRequest().authenticated();
                    } else {
                        requests.anyRequest().permitAll();
                    }
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Its own handlers, not the ones exceptionHandling() above installs: the
                        // bearer-token filter refuses a token it cannot validate without ever
                        // reaching ExceptionTranslationFilter.
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors)
                        .authenticationManagerResolver(resolver));

        return http.build();
    }
}
