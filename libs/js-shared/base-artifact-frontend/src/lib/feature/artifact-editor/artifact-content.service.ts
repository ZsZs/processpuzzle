import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BaseConfiguration, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { ArtifactBlock } from '../../domain/base-artifact';

/**
 * Deliberately not a BaseEntityRestService subclass — blocks aren't a BaseEntity with their own
 * list/form route, they're a sub-resource of one artifact, addressed and reordered as a group.
 * Every method here maps to exactly one block-level operation in base-artifact-api.yaml.
 */
@Injectable({ providedIn: 'root' })
export class ArtifactContentService {
  private readonly httpClient = inject(HttpClient);
  private readonly headers = {
    'Content-Type': 'application/json; charset=utf-8',
  };
  private readonly baseUrl = inject<{ BASE_CONFIGURATION: BaseConfiguration }>(RUNTIME_CONFIGURATION).BASE_CONFIGURATION.ARTIFACT_SERVICE_ROOT;

  private artifactUrl(artifactId: string): string {
    return `${this.baseUrl}/artifacts/${artifactId}`;
  }

  appendBlock(artifactId: string, block: Omit<ArtifactBlock, 'id'>): Promise<ArtifactBlock> {
    return firstValueFrom(
      this.httpClient.post<ArtifactBlock>(`${this.artifactUrl(artifactId)}/blocks`, block, { headers: this.headers }),
    );
  }

  replaceBlock(artifactId: string, blockId: string, block: Omit<ArtifactBlock, 'id'>): Promise<ArtifactBlock> {
    return firstValueFrom(
      this.httpClient.put<ArtifactBlock>(`${this.artifactUrl(artifactId)}/blocks/${blockId}`, block, { headers: this.headers }),
    );
  }

  deleteBlock(artifactId: string, blockId: string): Promise<void> {
    return firstValueFrom(this.httpClient.delete<void>(`${this.artifactUrl(artifactId)}/blocks/${blockId}`));
  }

  reorderBlocks(artifactId: string, blockIds: string[]): Promise<ArtifactBlock[]> {
    return firstValueFrom(
      this.httpClient.put<ArtifactBlock[]>(`${this.artifactUrl(artifactId)}/blocks/reorder`, { blockIds }, { headers: this.headers }),
    );
  }
}
