export interface EnvironmentVariables {
  readonly FIREBASE_API_KEY: string;
  readonly PIPELINE_STAGE?: 'dev' | 'ci' | 'stage' | 'prod';
}
