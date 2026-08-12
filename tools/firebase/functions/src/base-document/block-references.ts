import type { DocumentBlock } from './base-document.model.js';

/**
 * Which other blocks point at a given block — the one part of
 * `DocumentReferentialIntegrityChecker` this function needs.
 *
 * Only `referencesTo` is ported, not the whole checker: `deleteDocumentBlock` answers 409 when a
 * block is still referenced, and `DocumentContentStore` (`document-content.store.ts:129-131`)
 * propagates that status to the user, so getting it wrong deletes a widget that a paragraph still
 * embeds and leaves the editor rendering "missing widget". The remaining validation rules only
 * matter to `validateDocument` and the publish gate, which this function does not serve yet.
 *
 * A block is referenced two ways, matching the Java implementation exactly:
 *  - another block's `props.childIds` contains its id — checked on every block, not just widgets,
 *    because a widget type may compose children without declaring itself one;
 *  - another block's Tiptap `content` contains a `widgetEmbed` node whose `attrs.blockId` is its id.
 *    The frontend writes exactly that shape (`widget-embed-node.ts:27,39`).
 *
 * References never cross a locale boundary: `childIds` resolves inside one translation, so two
 * languages may reuse block ids without colliding.
 */

const WIDGET_EMBED_NODE_TYPE = 'widgetEmbed';
const CHILD_IDS_PROP = 'childIds';

export function referencesTo(blocks: readonly DocumentBlock[], blockId: string): string[] {
  return blocks.filter((block) => block.id !== blockId && (childIdsOf(block).includes(blockId) || containsWidgetEmbed(block.content, blockId))).map((block) => block.id);
}

function childIdsOf(block: DocumentBlock): string[] {
  const raw = block.props?.[CHILD_IDS_PROP];
  if (!Array.isArray(raw)) return [];
  return raw.filter((entry): entry is string => typeof entry === 'string');
}

/**
 * Walks the Tiptap tree generically rather than against a schema — each widget type owns its own
 * content shape, and the node may sit at any depth inside a paragraph, list item or table cell.
 */
function containsWidgetEmbed(node: unknown, blockId: string): boolean {
  if (Array.isArray(node)) return node.some((child) => containsWidgetEmbed(child, blockId));
  if (typeof node !== 'object' || node === null) return false;

  const candidate = node as { type?: unknown; attrs?: { blockId?: unknown } };
  if (candidate.type === WIDGET_EMBED_NODE_TYPE && candidate.attrs?.blockId === blockId) return true;

  return Object.values(node).some((value) => containsWidgetEmbed(value, blockId));
}
