import { EnvironmentVariables } from './environment-variables';

// The build-time fallback, used only when `assets/runtime-env.json` cannot be fetched. The deployed
// stage normally comes from that file, which the container's entrypoint renders from the environment.
export const environment: EnvironmentVariables = {
  PIPELINE_STAGE: 'dev',
};
