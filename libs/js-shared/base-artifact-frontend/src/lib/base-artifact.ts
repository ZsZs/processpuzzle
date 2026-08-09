export const DEFAULT_ARTIFACT_CONTENT_TYPE = 'application/octet-stream';

export class BaseArtifact {
  constructor(
    readonly name: string,
    readonly contentType = DEFAULT_ARTIFACT_CONTENT_TYPE,
  ) {}

  get isBinary(): boolean {
    return this.contentType === DEFAULT_ARTIFACT_CONTENT_TYPE;
  }

  describe(): string {
    return this.isBinary ? this.name : `${this.name} (${this.contentType})`;
  }
}
