import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ContentBook, ContentBookNode } from './content-book';

interface ContentBookNodeDto {
  id: string;
  documentId: string;
  parentId?: string | null;
  orderIndex?: number;
}

export interface ContentBookDto {
  id?: string;
  orgKey?: string;
  slug: string;
  title: string;
  description?: string | null;
  isPublic?: boolean;
  readerRoles?: string[];
  editorRoles?: string[];
  nodes?: ContentBookNodeDto[];
  lockVersion?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

interface ContentBookInputDto {
  slug: string;
  title: string;
  description?: string;
  isPublic: boolean;
  readerRoles: string[];
  editorRoles: string[];
}

@Injectable({ providedIn: 'root' })
export class ContentBookMapper implements BaseEntityMapper<ContentBook> {
  fromDto(dto: ContentBookDto): ContentBook {
    return new ContentBook(
      dto.id,
      dto.orgKey,
      dto.slug,
      dto.title,
      dto.description ?? undefined,
      dto.isPublic ?? false,
      dto.readerRoles ?? [],
      dto.editorRoles ?? [],
      (dto.nodes ?? []).map((node): ContentBookNode => ({ id: node.id, documentId: node.documentId, parentId: node.parentId ?? null, orderIndex: node.orderIndex ?? 0 })),
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
  toDto(entity: ContentBook): ContentBookInputDto {
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
