export interface FirebaseConfig {
  apiKey?: string;
  FIRESTORE_EMULATOR_HOST?: string;
  FIRESTORE_EMULATOR_PORT?: number;
  FIREBASE_AUTH_EMULATOR_HOST?: string;
  FIREBASE_AUTH_EMULATOR_PORT?: number;
}
export interface BaseConfiguration {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly BACKEND_SERVICE_PROVIDER: 'rest' | 'firestore';
  readonly BACKEND_SERVICE_ROOT: string;
  readonly OBJECT_STORE_SERVICE_ROOT: string;
  readonly RULE_SERVICE_ROOT: string;
  readonly FIREBASE_CONFIGURATION: FirebaseConfig;
}
