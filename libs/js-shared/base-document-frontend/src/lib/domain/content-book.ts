import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * One place of a document in a {@link ContentBook}'s tree. `id` is the client's to mint and unique within the
 * book; children name it as `parentId`. The server renumbers `orderIndex` per sibling list, so a client may
 * send any gaps it likes.
 */
export interface ContentBookNode {
  id: string;
  documentId: string;
  parentId?: string | null;
  orderIndex: number;
}

/**
 * A tree of existing documents with its own access rights — a Confluence space rather than a folder. The book
 * owns the tree, so a document is untouched by being filed in one and may be filed in several.
 *
 * Access follows the {@link Document} convention: `isPublic` opens reading to anyone, an empty role list means
 * any signed-in member. The roles guard the book, not the documents in it.
 */
export class ContentBook implements BaseEntity {
  constructor(
    public id?: string,
    public orgKey?: string,
    /** Route key, unique among the organization's books. What a `content-book` widget is pointed at. */
    public slug = '',
    public title = 'Untitled book',
    public description?: string,
    public isPublic = false,
    public readerRoles: string[] = [],
    public editorRoles: string[] = [],
    /** Not on the form: edited through the book's own tree, and saved with `PUT .../nodes`. */
    public nodes: ContentBookNode[] = [],
    public version?: number,
    public createdAt?: string,
    public updatedAt?: string,
  ) {}
}
