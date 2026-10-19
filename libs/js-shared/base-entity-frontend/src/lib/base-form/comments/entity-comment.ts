import type { RichTextContent } from '../../rich-text/rich-text-editor.component';

/**
 * One entry of a `COMMENTS` attribute, whose value is an array of these, oldest first.
 *
 * The same shape the backend validates and the seed data holds: `content` is Tiptap JSON, `createdAt` an
 * ISO-8601 instant. The history is append-only — the backend rejects a save that changes, drops or reorders a
 * stored entry — so nothing here is ever edited after it was saved.
 */
export interface EntityComment {
  /** The name the comment is shown under, as it was when the comment was written. */
  author: string;
  /** The author's user id, when the host knows one. */
  authorId?: string;
  createdAt: string;
  content: RichTextContent;
}

/** The value of a `COMMENTS` attribute as a list — anything that is not an array holds no comments. */
export function commentsOf(value: unknown): EntityComment[] {
  return Array.isArray(value) ? (value as EntityComment[]) : [];
}
