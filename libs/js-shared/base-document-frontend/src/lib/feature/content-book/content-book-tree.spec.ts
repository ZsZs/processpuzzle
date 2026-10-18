import { describe, expect, it } from 'vitest';
import { ContentBookNode } from '../../domain/content-book';
import { addNode, canIndent, canMove, canOutdent, detachDocument, indent, move, normalize, outdent, removeSubtree, siblingsOf, toTreeNav } from './content-book-tree';

const node = (id: string, parentId: string | null, orderIndex: number): ContentBookNode => ({ id, documentId: `doc-${id}`, parentId, orderIndex });

/**
 *  a
 *  ├─ a1
 *  │  └─ a1x
 *  └─ a2
 *  b
 */
const BOOK: ContentBookNode[] = [node('b', null, 1), node('a2', 'a', 1), node('a1x', 'a1', 0), node('a', null, 0), node('a1', 'a', 0)];

const shape = (nodes: ContentBookNode[]): string[] => nodes.map((each) => `${each.parentId ?? '-'}/${each.id}@${each.orderIndex}`);

describe('content book tree', () => {
  it('normalizes to depth-first order with siblings numbered from zero', () => {
    expect(shape(normalize([node('x', null, 7), node('y', null, 3), node('y1', 'y', 9)]))).toEqual(['-/y@0', 'y/y1@0', '-/x@1']);
  });

  it('lists a node’s siblings in order', () => {
    expect(siblingsOf(BOOK, 'a').map((each) => each.id)).toEqual(['a1', 'a2']);
    expect(siblingsOf(BOOK, undefined).map((each) => each.id)).toEqual(['a', 'b']);
  });

  it('builds tree-nav nodes, labelled by the caller', () => {
    const tree = toTreeNav(BOOK, (documentId) => ({ label: documentId.toUpperCase() }));

    expect(tree.map((each) => each.label)).toEqual(['DOC-A', 'DOC-B']);
    expect(tree[0].children?.map((each) => each.id)).toEqual(['a1', 'a2']);
    expect(tree[0].children?.[0].children?.[0].id).toBe('a1x');
    expect(tree[1].children).toBeUndefined();
  });

  it('adds a page last among its siblings, at the top level by default', () => {
    expect(shape(addNode(BOOK, 'doc-new', null, 'new')).at(-1)).toBe('-/new@2');
    expect(shape(addNode(BOOK, 'doc-new', 'a', 'new'))).toContain('a/new@2');
  });

  it('mints a node id when none is given', () => {
    const added = addNode([], 'doc-new');

    expect(added[0].id).toBeTruthy();
    expect(added[0].documentId).toBe('doc-new');
  });

  it('removes a page with everything filed under it', () => {
    expect(shape(removeSubtree(BOOK, 'a1'))).toEqual(['-/a@0', 'a/a2@0', '-/b@1']);
  });

  it('detaches a deleted document, moving its children up into its place', () => {
    expect(shape(detachDocument(BOOK, 'doc-a'))).toEqual(['-/a1@0', 'a1/a1x@0', '-/a2@1', '-/b@2']);
    expect(shape(detachDocument(BOOK, 'doc-unknown'))).toEqual(shape(normalize(BOOK)));
  });

  it('moves a page among its siblings, and not past either end', () => {
    expect(canMove(BOOK, 'a1', 'up')).toBe(false);
    expect(canMove(BOOK, 'a1', 'down')).toBe(true);
    expect(canMove(BOOK, 'missing', 'down')).toBe(false);

    expect(shape(move(BOOK, 'a1', 'down'))).toEqual(['-/a@0', 'a/a2@0', 'a/a1@1', 'a1/a1x@0', '-/b@1']);
    expect(shape(move(BOOK, 'b', 'up'))).toEqual(['-/b@0', '-/a@1', 'a/a1@0', 'a1/a1x@0', 'a/a2@1']);
    expect(shape(move(BOOK, 'a1', 'up'))).toEqual(shape(normalize(BOOK)));
  });

  it('indents a page under the sibling before it', () => {
    expect(canIndent(BOOK, 'a')).toBe(false);
    expect(canIndent(BOOK, 'a2')).toBe(true);

    expect(shape(indent(BOOK, 'a2'))).toEqual(['-/a@0', 'a/a1@0', 'a1/a1x@0', 'a1/a2@1', '-/b@1']);
    expect(shape(indent(BOOK, 'a'))).toEqual(shape(normalize(BOOK)));
  });

  it('outdents a page to right after its parent', () => {
    expect(canOutdent(BOOK, 'a')).toBe(false);
    expect(canOutdent(BOOK, 'a1')).toBe(true);

    expect(shape(outdent(BOOK, 'a1'))).toEqual(['-/a@0', 'a/a2@0', '-/a1@1', 'a1/a1x@0', '-/b@2']);
    expect(shape(outdent(BOOK, 'b'))).toEqual(shape(normalize(BOOK)));
  });
});
