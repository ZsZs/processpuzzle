import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Document, DocumentInputPort, DocumentKind, PortType } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';

/** A report template that can be run for one object of an entity, and the port the object goes into. */
export interface EntityReportTemplate {
  document: Document;
  port: DocumentInputPort;
}

/**
 * The organization's report templates, by the entity whose objects they report on: a `REPORT_TEMPLATE` with an
 * `ENTITY_REF` port of that entity type is a report of one such object — the invoice of one order.
 *
 * Fetched once and memoized as a promise, like `EntityDefinitionRegistry`: every entity's screens ask, and
 * they ask concurrently while an application's routes are built.
 */
@Injectable({ providedIn: 'root' })
export class EntityReportRegistry {
  private readonly documentService = inject(BaseDocumentService);
  private templates?: Promise<Document[]>;

  async templatesFor(entityName: string | undefined): Promise<EntityReportTemplate[]> {
    if (!entityName) return [];
    this.templates ??= this.fetch();
    return (await this.templates).flatMap((document) => {
      const port = document.inputPorts.find((candidate) => candidate.type === PortType.ENTITY_REF && candidate.entityType === entityName);
      return port ? [{ document, port }] : [];
    });
  }

  /** Discards the cache — after a template is created or its ports change. */
  reset(): void {
    this.templates = undefined;
  }

  private async fetch(): Promise<Document[]> {
    try {
      const response: unknown = await firstValueFrom(this.documentService.findAll());
      const documents = Array.isArray(response) ? response : ((response as { content?: Document[] } | undefined)?.content ?? []);
      return (documents as Document[]).filter((document) => document.kind === DocumentKind.REPORT_TEMPLATE);
    } catch {
      // No documents, or none readable: no Reports tab anywhere, rather than a failed route.
      return [];
    }
  }
}
