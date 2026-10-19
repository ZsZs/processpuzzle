import { Injectable } from '@angular/core';
import { ArtifactAttr, BaseEntityMapper } from '@processpuzzle/base-entity';
import { IssuedDocument } from './issued-document';

/** One `DocumentSignature` of the API, as `GET /issued-documents` returns it. */
interface IssuedDocumentDto extends Partial<Omit<IssuedDocument, 'name' | 'pdf'>> {
  pdf?: ArtifactAttr | null;
}

@Injectable({ providedIn: 'root' })
export class IssuedDocumentMapper implements BaseEntityMapper<IssuedDocument> {
  fromDto(dto: IssuedDocumentDto): IssuedDocument {
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
  toDto(entity: IssuedDocument): IssuedDocument {
    return { ...entity };
  }
}
