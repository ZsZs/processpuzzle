/** Frontend model of `ModuleAssetInfo` in `base-app-api.yaml`: one file a module brings along, without its bytes. */
export interface ModuleAsset {
  /** Relative to the module, e.g. `docs/img/flow.png`; what a widget names as `module:<path>`. */
  readonly path: string;
  readonly mimeType: string;
  /** Bytes. */
  readonly size: number;
  /** Hex SHA-256 of the content. */
  readonly sha256: string;
}
