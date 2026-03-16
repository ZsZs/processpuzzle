// eslint-disable-next-line @nx/enforce-module-boundaries
import { LanguageConfig } from '@processpuzzle/widgets';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthenticationConfiguration } from '@processpuzzle/auth';
import { BaseConfiguration } from '@processpuzzle/util';

export interface RuntimeConfiguration extends BaseConfiguration, LanguageConfig, AuthenticationConfiguration {
  BASE_CONFIGURATION: BaseConfiguration;
  LANGUAGE_CONFIGURATION: LanguageConfig;
  AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration;
}
