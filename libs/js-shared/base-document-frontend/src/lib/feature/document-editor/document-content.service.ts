import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BaseConfiguration, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { DocumentBlock } from '../../domain/base-document';

/**
 * Deliberately not a BaseEntityRestService subclass — blocks aren't a BaseEntity with their own
 * list/form route, they're a sub-resource of one document, addressed and reordered as a group.
 * Every method here maps to exactly one block-level operation in base-document-api.yaml.
 */
@Injectable({ providedIn: 'root' })
export class DocumentContentService {
  private readonly httpClient = inject(HttpClient);
  private readonly headers = {
    'Content-Type': 'application/json; charset=utf-8',
  };
  private readonly baseUrl = inject<{ BASE_CONFIGURATION: BaseConfiguration }>(RUNTIME_CONFIGURATION).BASE_CONFIGURATION.DOCUMENT_SERVICE_ROOT;

  private documentUrl(documentId: string): string {
    return `${this.baseUrl}/documents/${documentId}`;
  }

  appendBlock(documentId: string, block: Omit<DocumentBlock, 'id'>): Promise<DocumentBlock> {
    return firstValueFrom(
      this.httpClient.post<DocumentBlock>(`${this.documentUrl(documentId)}/blocks`, block, { headers: this.headers }),
    );
  }

  replaceBlock(documentId: string, blockId: string, block: Omit<DocumentBlock, 'id'>): Promise<DocumentBlock> {
    return firstValueFrom(
      this.httpClient.put<DocumentBlock>(`${this.documentUrl(documentId)}/blocks/${blockId}`, block, { headers: this.headers }),
    );
  }

  deleteBlock(documentId: string, blockId: string): Promise<void> {
    return firstValueFrom(this.httpClient.delete<void>(`${this.documentUrl(documentId)}/blocks/${blockId}`));
  }

  reorderBlocks(documentId: string, blockIds: string[]): Promise<DocumentBlock[]> {
    return firstValueFrom(
      this.httpClient.put<DocumentBlock[]>(`${this.documentUrl(documentId)}/blocks/reorder`, { blockIds }, { headers: this.headers }),
    );
  }
}
