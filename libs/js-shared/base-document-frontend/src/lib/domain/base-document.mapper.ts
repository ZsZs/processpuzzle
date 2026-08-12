import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Document, DocumentInputPort, DocumentOutputPort } from './base-document';

@Injectable({ providedIn: 'root' })
export class BaseDocumentMapper implements BaseEntityMapper<Document> {
  fromDto(dto: any): Document {
    return new Document(
      dto.id,
      dto.orgKey,
      dto.slug,
      dto.title,
      dto.subject,
      dto.description,
      dto.author,
      dto.sourceLocale,
      dto.isPublic ?? false,
      // The three role lists are `nullable` on a summary and absent from an older payload; a form control
      // bound to `undefined` renders as an empty chip grid, but saving it back would send `null` where the
      // contract wants a list. Normalizing here keeps that out of every caller.
      dto.readerRoles ?? [],
      dto.editorRoles ?? [],
      dto.publisherRoles ?? [],
      (dto.inputPorts ?? []).map((p: any) => new DocumentInputPort(
        p.name, p.type, p.required, p.description, p.defaultValue,
        p.entityType, p.attributeVisibility, p.defaultRsqlFilter,
      )),
      (dto.outputPorts ?? []).map((p: any) => new DocumentOutputPort(
        p.name, p.type, p.description, p.entityType, p.attributeVisibility,
      )),
      dto.translations ?? [],
      dto.translation ?? undefined,
      dto.version,
      dto.createdAt,
      dto.updatedAt,
    );
  }

  /**
   * Used by the generic `BaseEntityStore.create()` path, which POSTs it as a `DocumentInput`. Every
   * language-invariant field the form carries is on the entity, so spreading it is enough — and `translations`
   * travels with it as the empty list, which the server answers by starting a draft in `sourceLocale`.
   *
   * Deliberately NOT what a Properties *save* submits — see BaseDocumentService.updateProperties, which builds
   * a `DocumentPropertiesInput`-shaped body by hand so that a save cannot reach the block list even by
   * accident.
   */
  toDto(entity: Document): any {
    return { ...entity };
  }
}
