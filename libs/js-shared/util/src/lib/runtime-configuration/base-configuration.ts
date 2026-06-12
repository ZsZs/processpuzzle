export interface FirebaseConfig {
  apiKey?: string;
}
export interface BaseConfiguration {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly BACKEND_SERVICE_PROVIDER: 'rest' | 'firestore';
  readonly BACKEND_SERVICE_ROOT: string;
  readonly OBJECT_STORE_SERVICE_ROOT: string;
  readonly FIREBASE_CONFIGURATION: FirebaseConfig;
}
