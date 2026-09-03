import { bootstrapApplication } from '@angular/platform-browser';
import { ConfigurationService } from '@processpuzzle/util';
import { currentOrgKey, KeycloakAuthConfig, resolveTenantRealm } from '@processpuzzle/auth/domain';
import { environment as buildTimeEnv } from './environments/environment';
import { EnvironmentVariables } from './environments/environment-variables';
import { AppComponent } from './app/app.component';
import { createAppConfig } from './app/app.config';
import { RuntimeConfiguration } from './app/runtime-configuration';

/**
 * The container's rendered environment, or the build-time one when the file is not there.
 *
 * `assets/runtime-env.json` is written by the Docker entrypoint from `envsubst`, which is what lets
 * one image serve every stage. `cache: 'no-store'` because a cached copy would pin the first stage
 * the browser ever saw.
 */
async function loadEnvironment(): Promise<EnvironmentVariables> {
  try {
    const response = await fetch('assets/runtime-env.json', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as EnvironmentVariables;
  } catch (error) {
    console.warn('runtime-env.json not available, using build-time environment', error);
    return buildTimeEnv as EnvironmentVariables;
  }
}

/**
 * Substitutes the tenant read from the URL into the configured realm template.
 *
 * One bundle serves every tenant, so the realm is not knowable at build time. It has to be settled
 * *here*, before `bootstrapApplication`: `KeycloakAuthService` constructs its `Keycloak` instance
 * eagerly from `AUTH_SERVICE_CONFIG.realm`, and the app initializer that restores an SSO session
 * runs immediately after. A route resolver would be far too late.
 *
 * `AUTH_SERVICE_CONFIG` is `readonly`, so this returns a new configuration rather than writing
 * through it. When the template names no `{orgKey}` — or the URL names no plausible tenant — the
 * configuration is handed back untouched and the fixed realm applies.
 */
function withTenantRealm(runtimeConfiguration: RuntimeConfiguration): RuntimeConfiguration {
  const authenticationConfiguration = runtimeConfiguration.AUTHENTICATION_CONFIGURATION;
  const authServiceConfig = authenticationConfiguration.AUTH_SERVICE_CONFIG as KeycloakAuthConfig | undefined;
  if (!authServiceConfig?.realm?.includes('{orgKey}')) return runtimeConfiguration;

  const realm = resolveTenantRealm(authServiceConfig.realm, undefined, authenticationConfiguration.FALLBACK_AUTH_REALM);
  return {
    ...runtimeConfiguration,
    AUTHENTICATION_CONFIGURATION: {
      ...authenticationConfiguration,
      AUTH_SERVICE_CONFIG: { ...authServiceConfig, realm },
    },
  };
}

async function bootstrap() {
  const env = await loadEnvironment();
  const configurationService = new ConfigurationService<EnvironmentVariables, RuntimeConfiguration>();
  const loadedConfiguration = (await configurationService.init(env)) as RuntimeConfiguration;
  const runtimeConfiguration = withTenantRealm(loadedConfiguration);

  // Read once, from the same path segment the realm came from, and handed to the application as a
  // value. `ORG_ADMIN_ORG_KEY` is injected when a root-provided service is constructed, which is
  // long before any route activates, so it cannot be a route parameter — and the routes themselves
  // are built around this key, so a URL naming a different tenant does not match at all. Switching
  // tenants is a full page load, which is also what stops org B's data being fetched with org A's
  // token.
  const orgKey = currentOrgKey();

  await bootstrapApplication(AppComponent, createAppConfig(runtimeConfiguration, orgKey));
}

bootstrap().catch((error) => console.error(error));
