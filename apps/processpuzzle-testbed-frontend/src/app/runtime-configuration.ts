import { BaseConfiguration, LanguageConfig, LoggingConfiguration } from '@processpuzzle/util';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthenticationConfiguration } from '@processpuzzle/auth';

export interface RuntimeConfiguration extends BaseConfiguration, LanguageConfig, AuthenticationConfiguration {
  BASE_CONFIGURATION: BaseConfiguration;
  LANGUAGE_CONFIGURATION: LanguageConfig;
  AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration;
  LOGGING_CONFIGURATION: LoggingConfiguration;
}
