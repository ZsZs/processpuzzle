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

  /**
   * Blocks belong to a *locale's* draft, not to the document — the contract scopes every block operation to
   * `translations/{locale}` and the writes land on that locale's draft, leaving published content alone
   * until publishDocumentTranslation. Which is why `locale` is a parameter of all four calls rather than
   * state of this service: one editor session edits one locale, but nothing here should assume that.
   */
  private blocksUrl(documentId: string, locale: string): string {
    return `${this.baseUrl}/documents/${documentId}/translations/${locale}/blocks`;
  }

  appendBlock(documentId: string, locale: string, block: Omit<DocumentBlock, 'id'>): Promise<DocumentBlock> {
    return firstValueFrom(
      this.httpClient.post<DocumentBlock>(this.blocksUrl(documentId, locale), block, { headers: this.headers }),
    );
  }

  replaceBlock(documentId: string, locale: string, blockId: string, block: Omit<DocumentBlock, 'id'>): Promise<DocumentBlock> {
    return firstValueFrom(
      this.httpClient.put<DocumentBlock>(`${this.blocksUrl(documentId, locale)}/${blockId}`, block, { headers: this.headers }),
    );
  }

  deleteBlock(documentId: string, locale: string, blockId: string): Promise<void> {
    return firstValueFrom(this.httpClient.delete<void>(`${this.blocksUrl(documentId, locale)}/${blockId}`));
  }

  reorderBlocks(documentId: string, locale: string, blockIds: string[]): Promise<DocumentBlock[]> {
    return firstValueFrom(
      this.httpClient.put<DocumentBlock[]>(`${this.blocksUrl(documentId, locale)}/reorder`, { blockIds }, { headers: this.headers }),
    );
  }
}
