export class RuntimeConfiguration {
  readonly LANGUAGE?: string;
  readonly PIPELINE_STAGE?: 'DEV' | 'CI' | 'STAGE' | 'PROD';
  readonly TEST_SERVICE_ROOT?: string;
  readonly FIREBASE_CONFIG?: object;
}
