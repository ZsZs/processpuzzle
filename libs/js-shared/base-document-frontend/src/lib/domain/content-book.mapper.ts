import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ContentBook, ContentBookNode } from './content-book';

@Injectable({ providedIn: 'root' })
export class ContentBookMapper implements BaseEntityMapper<ContentBook> {
  fromDto(dto: any): ContentBook {
    return new ContentBook(
      dto.id,
      dto.orgKey,
      dto.slug,
      dto.title,
      dto.description ?? undefined,
      dto.isPublic ?? false,
      dto.readerRoles ?? [],
      dto.editorRoles ?? [],
      (dto.nodes ?? []).map((node: any): ContentBookNode => ({ id: node.id, documentId: node.documentId, parentId: node.parentId ?? null, orderIndex: node.orderIndex ?? 0 })),
      dto.lockVersion ?? dto.version,
      dto.createdAt,
      dto.updatedAt,
    );
  }

  /**
   * A `ContentBookInput` — and deliberately without `nodes`: the generic form does not edit the tree, and a
   * save that sent the tree it loaded would revert a reorder made in the meantime. Absent `nodes` is what the
   * contract reads as "leave the tree alone".
   */
  toDto(entity: ContentBook): any {
    return {
      slug: entity.slug,
      title: entity.title,
      description: entity.description,
      isPublic: entity.isPublic,
      readerRoles: entity.readerRoles,
      editorRoles: entity.editorRoles,
    };
  }
}
