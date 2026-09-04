// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthenticationConfiguration } from '@processpuzzle/auth';
import { BaseConfiguration, LanguageConfig, LoggingConfiguration } from '@processpuzzle/util';

/**
 * Shape of the deep-merged `run-time-conf/config.*.json` files.
 *
 * The four sections are what `ConfigurationService` merges and what `createAppConfig` reads. It
 * extends the section interfaces as well as naming them as properties because that is the contract
 * `ConfigurationService` was written against.
 */
export interface RuntimeConfiguration extends BaseConfiguration, LanguageConfig, AuthenticationConfiguration {
  BASE_CONFIGURATION: BaseConfiguration;
  LANGUAGE_CONFIGURATION: LanguageConfig;
  AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration;
  LOGGING_CONFIGURATION: LoggingConfiguration;
}
