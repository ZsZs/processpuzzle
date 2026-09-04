import { bootstrapApplication } from '@angular/platform-browser';
import { ConfigurationService } from '@processpuzzle/util';
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
 * No realm resolution here, unlike `processpuzzle-biz-frontend`.
 *
 * This application administers every tenant, so its users are staff of the platform and belong to
 * the fixed `processpuzzle-admin` realm named in `run-time-conf/config.common.json`. Reading a
 * realm out of the URL would be actively wrong: the first path segment of `/organization/acme` is a
 * screen, not a tenant.
 */
async function bootstrap() {
  const env = await loadEnvironment();
  const configurationService = new ConfigurationService<EnvironmentVariables, RuntimeConfiguration>();
  const runtimeConfiguration = (await configurationService.init(env)) as RuntimeConfiguration;

  await bootstrapApplication(AppComponent, createAppConfig(runtimeConfiguration));
}

bootstrap().catch((error) => console.error(error));
