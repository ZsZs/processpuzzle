import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { IssuedDocument } from './issued-document';

@Injectable({ providedIn: 'root' })
export class IssuedDocumentMapper implements BaseEntityMapper<IssuedDocument> {
  fromDto(dto: any): IssuedDocument {
    return new IssuedDocument(
      dto.id,
      `${dto.title ?? ''} – ${dto.subjectId ?? ''}`,
      dto.documentId,
      dto.title ?? '',
      dto.locale,
      dto.revision,
      dto.subjectType,
      dto.subjectId,
      dto.signedBy,
      dto.signedAt,
      dto.pdf ?? undefined,
    );
  }

  /** Never sent: the entity is read-only, and the server answers 405 to anything but a read. */
  toDto(entity: IssuedDocument): any {
    return { ...entity };
  }
}
