import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Document, DocumentInputPort, DocumentOutputPort } from './base-document';

@Injectable({ providedIn: 'root' })
export class BaseDocumentMapper implements BaseEntityMapper<Document> {
  fromDto(dto: any): Document {
    return new Document(
      dto.id,
      dto.orgKey,
      dto.title,
      dto.description,
      (dto.inputPorts ?? []).map((p: any) => new DocumentInputPort(
        p.name, p.type, p.required, p.description, p.defaultValue,
        p.entityType, p.attributeVisibility, p.defaultRsqlFilter,
      )),
      (dto.outputPorts ?? []).map((p: any) => new DocumentOutputPort(
        p.name, p.type, p.description, p.entityType, p.attributeVisibility,
      )),
      dto.blocks ?? [],
      dto.version,
      dto.createdAt,
      dto.updatedAt,
    );
  }

  // Used only by the generic BaseEntityStore's create() path (there is no document create
  // form in this sketch, but BaseEntityRestService requires the mapper regardless) and, more
  // relevantly, is deliberately NOT what the Properties form submits — see
  // BaseDocumentService.updateProperties, which builds a DocumentPropertiesInput-shaped body
  // by hand rather than routing through this toDto/PUT-the-whole-entity path.
  toDto(entity: Document): any {
    return { ...entity };
  }
}
