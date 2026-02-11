import { LanguageConfig } from '@processpuzzle/widgets';
import { AuthenticationConfiguration } from '@processpuzzle/auth';
import { BaseConfiguration } from '@processpuzzle/util';

export interface RuntimeConfiguration extends BaseConfiguration, LanguageConfig, AuthenticationConfiguration {
  BASE_CONFIGURATION: BaseConfiguration;
  LANGUAGE_CONFIGURATION: LanguageConfig;
  AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration;
}
