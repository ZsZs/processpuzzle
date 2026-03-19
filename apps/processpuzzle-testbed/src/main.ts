import { bootstrapApplication } from '@angular/platform-browser';
import { environment } from './environments/environment';
import { createAppConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { BaseConfiguration, ConfigurationService, FirebaseConfig } from '@processpuzzle/util';
import { EnvironmentVariables } from './environments/environment-variables';
import { RuntimeConfiguration } from './app/runtime-configuration';

function mergeFirebaseApiKey(env: EnvironmentVariables, runtimeConfig: RuntimeConfiguration) {
  let baseConfig: BaseConfiguration = runtimeConfig.BASE_CONFIGURATION;
  const apiKey = { apiKey: environment.FIREBASE_API_KEY ?? '' };
  const firebaseConfig: FirebaseConfig = { ...baseConfig.FIREBASE_CONFIGURATION, ...apiKey };
  baseConfig = { ...baseConfig, ...{ FIREBASE_CONFIGURATION: firebaseConfig } };
  return { ...runtimeConfig, ...{ BASE_CONFIGURATION: baseConfig } };
}
async function bootstrap() {
  const env = environment as EnvironmentVariables;
  const configurationService = new ConfigurationService<EnvironmentVariables, RuntimeConfiguration>();
  const runtimeConfig = (await configurationService.init(env)) as RuntimeConfiguration;

  await bootstrapApplication(AppComponent, createAppConfig(mergeFirebaseApiKey(env, runtimeConfig)));
}

bootstrap().catch((err) => console.error(err));
