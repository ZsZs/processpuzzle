export interface FirebaseConfig {
  apiKey?: string;
  FIRESTORE_EMULATOR_HOST?: string;
  FIRESTORE_EMULATOR_PORT?: number;
  FIREBASE_AUTH_EMULATOR_HOST?: string;
  FIREBASE_AUTH_EMULATOR_PORT?: number;
}
export interface BaseConfiguration {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly APPLICATION_VERSION: string;
  readonly DEPLOYMENT_ENVIRONMENT: 'docker' | 'k8s' | 'firebase';
  readonly BACKEND_SERVICE_PROVIDER: 'rest' | 'firestore';
  readonly BACKEND_SERVICE_ROOT: string;
  readonly OBJECT_STORE_SERVICE_ROOT: string;
  readonly RULE_SERVICE_ROOT: string;
  /** Organization-scoped root of the base-app endpoints: `<host>/organizations/<orgKey>`. */
  readonly APP_SERVICE_ROOT: string;
  /** Organization-scoped root of the base-artifact endpoints: `<host>/organizations/<orgKey>`. */
  readonly ARTIFACT_SERVICE_ROOT: string;
  readonly FIREBASE_CONFIGURATION: FirebaseConfig;
}
