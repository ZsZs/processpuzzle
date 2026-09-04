export interface EnvironmentVariables {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly CONFIGURATION_OVERRIDES?: string[];
}
