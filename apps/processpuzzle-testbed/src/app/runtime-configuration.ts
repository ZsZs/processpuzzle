export class RuntimeConfiguration {
  readonly LANGUAGE?: string;
  readonly PIPELINE_STAGE?: 'DEV' | 'CI' | 'STAGE' | 'PROD';
  readonly ICS_BACKEND_ROOT?: string;
  readonly MESSAGE_SERVICE_ROOT?: string;
  readonly TEST_SERVICE_ROOT?: string;
  readonly FIREBASE_CONFIG?: object;
}
