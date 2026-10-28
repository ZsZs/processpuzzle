/** Frontend model of `ModuleAssetInfo` in `base-app-api.yaml`, which app assets share: one file an application brings along, without its bytes. */
export interface AppAsset {
  /** Relative to the application, e.g. `cards/plan-race.svg`; what a widget names as `app:<path>`. */
  readonly path: string;
  readonly mimeType: string;
  /** Bytes. */
  readonly size: number;
  /** Hex SHA-256 of the content. */
  readonly sha256: string;
}
