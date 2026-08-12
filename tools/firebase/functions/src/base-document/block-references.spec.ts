import { describe, expect, it } from 'vitest';
import { referencesTo } from './block-references.js';
import { anEmbeddingBlock, aTextBlock, aWidgetBlock } from './test-support.js';

describe('referencesTo', () => {
  it('is empty when nothing points at the block', () => {
    expect(referencesTo([aTextBlock('text-1'), aWidgetBlock('widget-1')], 'widget-1')).toEqual([]);
  });

  it('finds a widgetEmbed node nested anywhere in another block content', () => {
    const blocks = [aWidgetBlock('widget-1'), anEmbeddingBlock('text-1', 'widget-1')];
    expect(referencesTo(blocks, 'widget-1')).toEqual(['text-1']);
  });

  it('finds an embed however deeply the editor nested it', () => {
    const deep = {
      id: 'text-1',
      kind: 'TEXT' as const,
      content: { type: 'doc', content: [{ type: 'table', content: [{ type: 'tableRow', content: [{ type: 'tableCell', content: [{ type: 'widgetEmbed', attrs: { blockId: 'widget-1' } }] }] }] }] },
    };
    expect(referencesTo([aWidgetBlock('widget-1'), deep], 'widget-1')).toEqual(['text-1']);
  });

  it('finds a props.childIds entry', () => {
    const parent = aWidgetBlock('widget-parent', { props: { childIds: ['widget-1', 'widget-2'] } });
    expect(referencesTo([aWidgetBlock('widget-1'), parent], 'widget-1')).toEqual(['widget-parent']);
  });

  it('checks childIds on every block, not only on widgets', () => {
    const text = { id: 'text-1', kind: 'TEXT' as const, props: { childIds: ['widget-1'] } };
    expect(referencesTo([aWidgetBlock('widget-1'), text], 'widget-1')).toEqual(['text-1']);
  });

  it('ignores non-string entries in childIds instead of throwing on them', () => {
    const parent = aWidgetBlock('widget-parent', { props: { childIds: [42, null, 'widget-1'] } });
    expect(referencesTo([aWidgetBlock('widget-1'), parent], 'widget-1')).toEqual(['widget-parent']);
  });

  it('ignores a childIds prop that is not an array', () => {
    const parent = aWidgetBlock('widget-parent', { props: { childIds: 'widget-1' } });
    expect(referencesTo([aWidgetBlock('widget-1'), parent], 'widget-1')).toEqual([]);
  });

  it('never reports the block itself, so a self-referencing embed does not make it undeletable', () => {
    const selfEmbedding = anEmbeddingBlock('widget-1', 'widget-1');
    expect(referencesTo([selfEmbedding], 'widget-1')).toEqual([]);
  });

  it('reports every referencing block, because the client names them all to the user', () => {
    const blocks = [aWidgetBlock('widget-1'), anEmbeddingBlock('text-1', 'widget-1'), aWidgetBlock('widget-parent', { props: { childIds: ['widget-1'] } })];
    expect(referencesTo(blocks, 'widget-1').sort()).toEqual(['text-1', 'widget-parent']);
  });

  it('does not confuse an embed of a different block for one of this block', () => {
    expect(referencesTo([anEmbeddingBlock('text-1', 'widget-2')], 'widget-1')).toEqual([]);
  });

  it('tolerates blocks with no content and no props', () => {
    expect(referencesTo([{ id: 'text-1', kind: 'TEXT' }], 'widget-1')).toEqual([]);
  });

  it('ignores a widgetEmbed node that carries no attrs', () => {
    const malformed = { id: 'text-1', kind: 'TEXT' as const, content: { type: 'doc', content: [{ type: 'widgetEmbed' }] } };
    expect(referencesTo([malformed], 'widget-1')).toEqual([]);
  });
});
