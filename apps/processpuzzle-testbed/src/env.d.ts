declare interface Env {
  readonly NODE_ENV: string;
  readonly NG_APP_PIPELINE_STAGE: string;
  readonly NG_APP_FIREBASE_TOKEN: string;
}

declare interface ImportMeta {
  readonly env: Env;
}

// You can modify the name of the variable in angular.json.
// ngxEnv: {
//  define: '_NGX_ENV_',
// }
declare const _NGX_ENV_: Env;
