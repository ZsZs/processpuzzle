export interface EnvironmentVariables {
  readonly FIREBASE_API_KEY: string;
  readonly PIPELINE_STAGE?: 'DEV' | 'CI' | 'STAGE' | 'PROD';
}
