export interface TestEnvironmentVariables {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly CONFIGURATION_OVERRIDES: string[];
}
