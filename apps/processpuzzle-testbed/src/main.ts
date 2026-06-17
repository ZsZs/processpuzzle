import { bootstrapApplication } from '@angular/platform-browser';
import { environment as buildTimeEnv } from './environments/environment';
import { createAppConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { BaseConfiguration, ConfigurationService, FirebaseConfig } from '@processpuzzle/util';
import { EnvironmentVariables } from './environments/environment-variables';
import { RuntimeConfiguration } from './app/runtime-configuration';

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

function mergeFirebaseApiKey(env: EnvironmentVariables, runtimeConfig: RuntimeConfiguration) {
  let baseConfig: BaseConfiguration = runtimeConfig.BASE_CONFIGURATION;
  const apiKey = { apiKey: env.FIREBASE_API_KEY ?? '' };
  const firebaseConfig: FirebaseConfig = { ...baseConfig.FIREBASE_CONFIGURATION, ...apiKey };
  baseConfig = { ...baseConfig, ...{ FIREBASE_CONFIGURATION: firebaseConfig } };
  return { ...runtimeConfig, ...{ BASE_CONFIGURATION: baseConfig } };
}

async function bootstrap() {
  const env = await loadEnvironment();
  const configurationService = new ConfigurationService<EnvironmentVariables, RuntimeConfiguration>();
  const runtimeConfig = (await configurationService.init(env)) as RuntimeConfiguration;

  await bootstrapApplication(AppComponent, createAppConfig(mergeFirebaseApiKey(env, runtimeConfig)));
}

bootstrap().catch((err) => console.error(err));
