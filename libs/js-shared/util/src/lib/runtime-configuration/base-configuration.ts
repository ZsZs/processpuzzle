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
  /**
   * Root of the *third-party* REST sources an application integrates with — not of the platform's own
   * features, which every deployment reaches through `APP_SERVICE_ROOT` and its per-feature siblings
   * below. In dev and CI this is the json-server mock (see `tools/mock-backend/README.md`).
   */
  readonly BACKEND_SERVICE_ROOT: string;
  readonly OBJECT_STORE_SERVICE_ROOT: string;
  readonly RULE_SERVICE_ROOT: string;
  /** Organization-scoped root of the base-app endpoints: `<host>/organizations/<orgKey>`. */
  readonly APP_SERVICE_ROOT: string;
  /** Organization-scoped root of the base-document endpoints: `<host>/organizations/<orgKey>`. */
  readonly DOCUMENT_SERVICE_ROOT: string;
  /**
   * Organization-scoped roots of the remaining features, same shape as the two above. Optional because
   * every feature is served by one host today: absent, a caller falls back to `APP_SERVICE_ROOT`.
   *
   * They exist so that a feature can move to a host of its own — each backend library is meant to become
   * a service with its own database — without every caller changing. The translations resource is the
   * first to use them, since it is the first resource all seven features expose.
   */
  readonly ENTITY_SERVICE_ROOT?: string;
  readonly WIDGET_SERVICE_ROOT?: string;
  readonly STATE_SERVICE_ROOT?: string;
  readonly WORKFLOW_SERVICE_ROOT?: string;
  readonly FIREBASE_CONFIGURATION: FirebaseConfig;
}
