import { TreeNavNode } from '@processpuzzle/widgets';
import { ContentBookNode } from '../../domain/content-book';

/**
 * Pure operations over a content book's flat node list. Every edit of the tree is one of these followed by
 * one `PUT .../nodes`, so the widget composes them and never mutates a node itself.
 *
 * Every function returns a normalized list: depth-first, siblings numbered 0..n-1 — the shape the server
 * stores, so what the widget shows after a save is what it sent.
 */

export type MoveDirection = 'up' | 'down';

/** A node's siblings, its own entry included, in their order. */
export function siblingsOf(nodes: readonly ContentBookNode[], parentId: string | null | undefined): ContentBookNode[] {
  return nodes.filter((node) => (node.parentId ?? null) === (parentId ?? null)).sort((a, b) => a.orderIndex - b.orderIndex);
}

export function normalize(nodes: readonly ContentBookNode[]): ContentBookNode[] {
  const ordered: ContentBookNode[] = [];
  const append = (parentId: string | null): void => {
    siblingsOf(nodes, parentId).forEach((node, index) => {
      ordered.push({ ...node, parentId, orderIndex: index });
      append(node.id);
    });
  };
  append(null);
  return ordered;
}

/**
 * The tree as {@link TreeNavNode}s. `labelOf` names a node's document; a document the reader may not see — the
 * listing drops it — still has its place in the book, and is shown under whatever label the caller picks.
 */
export function toTreeNav(nodes: readonly ContentBookNode[], labelOf: (documentId: string) => { label: string; icon?: string }): TreeNavNode[] {
  const build = (parentId: string | null): TreeNavNode[] =>
    siblingsOf(nodes, parentId).map((node) => {
      const children = build(node.id);
      return { id: node.id, ...labelOf(node.documentId), ...(children.length > 0 ? { children } : {}) };
    });
  return build(null);
}

/** Files `documentId` as the last child of `parentId`, or last at the top level. */
export function addNode(nodes: readonly ContentBookNode[], documentId: string, parentId: string | null = null, id: string = newNodeId()): ContentBookNode[] {
  return normalize([...nodes, { id, documentId, parentId, orderIndex: siblingsOf(nodes, parentId).length }]);
}

/** Takes a node and everything filed under it out of the book. The documents themselves are untouched. */
export function removeSubtree(nodes: readonly ContentBookNode[], nodeId: string): ContentBookNode[] {
  const removed = new Set([nodeId]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const node of nodes) {
      if (node.parentId && removed.has(node.parentId) && !removed.has(node.id)) {
        removed.add(node.id);
        grew = true;
      }
    }
  }
  return normalize(nodes.filter((node) => !removed.has(node.id)));
}

/**
 * Takes the node showing `documentId` out, moving its children up into its place — what the server does to
 * every book when the document itself is deleted, so the widget's tree matches the stored one without a reload.
 */
export function detachDocument(nodes: readonly ContentBookNode[], documentId: string): ContentBookNode[] {
  const removed = nodes.find((node) => node.documentId === documentId);
  if (!removed) return normalize(nodes);

  const parentId = removed.parentId ?? null;
  const reordered: ContentBookNode[] = [];
  siblingsOf(nodes, parentId).forEach((sibling) => {
    if (sibling.id === removed.id) siblingsOf(nodes, removed.id).forEach((child) => reordered.push({ ...child, parentId }));
    else reordered.push(sibling);
  });
  const placed = new Map(reordered.map((node, index) => [node.id, { ...node, orderIndex: index }]));
  return normalize(nodes.filter((node) => node.id !== removed.id).map((node) => placed.get(node.id) ?? node));
}

export function canMove(nodes: readonly ContentBookNode[], nodeId: string, direction: MoveDirection): boolean {
  const { siblings, index } = placeOf(nodes, nodeId);
  return index >= 0 && (direction === 'up' ? index > 0 : index < siblings.length - 1);
}

/** Swaps a node with the sibling before or after it. */
export function move(nodes: readonly ContentBookNode[], nodeId: string, direction: MoveDirection): ContentBookNode[] {
  if (!canMove(nodes, nodeId, direction)) return normalize(nodes);

  const { siblings, index } = placeOf(nodes, nodeId);
  const other = siblings[direction === 'up' ? index - 1 : index + 1];
  const node = siblings[index];
  const swapped = (each: ContentBookNode): ContentBookNode => {
    if (each.id === node.id) return { ...each, orderIndex: other.orderIndex };
    if (each.id === other.id) return { ...each, orderIndex: node.orderIndex };
    return each;
  };
  return normalize(nodes.map(swapped));
}

export function canIndent(nodes: readonly ContentBookNode[], nodeId: string): boolean {
  return placeOf(nodes, nodeId).index > 0;
}

/** Makes a node the last child of the sibling before it. */
export function indent(nodes: readonly ContentBookNode[], nodeId: string): ContentBookNode[] {
  if (!canIndent(nodes, nodeId)) return normalize(nodes);

  const { siblings, index } = placeOf(nodes, nodeId);
  const newParent = siblings[index - 1];
  const orderIndex = siblingsOf(nodes, newParent.id).length;
  return normalize(nodes.map((each) => (each.id === nodeId ? { ...each, parentId: newParent.id, orderIndex } : each)));
}

export function canOutdent(nodes: readonly ContentBookNode[], nodeId: string): boolean {
  return !!nodes.find((node) => node.id === nodeId)?.parentId;
}

/** Makes a node the sibling right after its parent. */
export function outdent(nodes: readonly ContentBookNode[], nodeId: string): ContentBookNode[] {
  if (!canOutdent(nodes, nodeId)) return normalize(nodes);

  const parent = nodes.find((node) => node.id === nodes.find((each) => each.id === nodeId)?.parentId) as ContentBookNode;
  const grandparentId = parent.parentId ?? null;
  const reordered: ContentBookNode[] = [];
  siblingsOf(nodes, grandparentId).forEach((sibling) => {
    reordered.push(sibling);
    if (sibling.id === parent.id) reordered.push({ ...(nodes.find((node) => node.id === nodeId) as ContentBookNode), parentId: grandparentId });
  });
  const placed = new Map(reordered.map((node, index) => [node.id, { ...node, orderIndex: index }]));
  return normalize(nodes.map((node) => placed.get(node.id) ?? node));
}

function placeOf(nodes: readonly ContentBookNode[], nodeId: string): { siblings: ContentBookNode[]; index: number } {
  const node = nodes.find((each) => each.id === nodeId);
  if (!node) return { siblings: [], index: -1 };
  const siblings = siblingsOf(nodes, node.parentId);
  return { siblings, index: siblings.findIndex((each) => each.id === nodeId) };
}

let nodeCounter = 0;

function newNodeId(): string {
  nodeCounter += 1;
  return globalThis.crypto?.randomUUID?.() ?? `node-${Date.now().toString(36)}-${nodeCounter.toString(36)}`;
}
