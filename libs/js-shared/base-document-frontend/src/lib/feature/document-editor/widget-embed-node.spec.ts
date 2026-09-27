import { Component, EnvironmentInjector, input, OnDestroy, output, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { WidgetRegistration } from '@processpuzzle/widgets';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BlockKind, DocumentBlock, WidgetPlacement } from '../../domain/base-document';
import { createWidgetEmbedExtension } from './widget-embed-node';

@Component({
  selector: 'pp-embed-test-widget',
  template: `<button class="embed-test-widget" (click)="clicked.emit(label())">{{ label() }}</button>`,
})
class EmbedTestWidgetComponent implements OnDestroy {
  readonly label = input('');
  readonly clicked = output<string>();
  ngOnDestroy() {
    // Lifecycle hook observed by the teardown assertions.
  }
}

const REGISTRY: ReadonlyMap<string, WidgetRegistration> = new Map(['test-widget', 'other-widget'].map((type) => [type, { type, component: EmbedTestWidgetComponent, definition: { name: type } }]));

function widget(patch: Partial<DocumentBlock> = {}): DocumentBlock {
  return { id: 'widget-1', kind: BlockKind.WIDGET, placement: WidgetPlacement.REFERENCED, type: 'test-widget', props: { label: 'Hello' }, ...patch };
}

describe('widgetEmbed node', () => {
  let editor: Editor;
  const blocksById = signal<ReadonlyMap<string, DocumentBlock>>(new Map());
  const resolveBinding = vi.fn<(port: string) => unknown>();
  const publishOutput = vi.fn();

  beforeEach(() => {
    TestBed.configureTestingModule({});
    blocksById.set(new Map());
    resolveBinding.mockReset();
    publishOutput.mockReset();
  });

  afterEach(() => {
    editor?.destroy();
    vi.restoreAllMocks();
  });

  function setBlocks(blocks: DocumentBlock[]) {
    blocksById.set(new Map(blocks.map((block) => [block.id, block])));
    TestBed.tick();
  }

  function render(blocks: DocumentBlock[], blockId: string | null = 'widget-1') {
    blocksById.set(new Map(blocks.map((block) => [block.id, block])));
    editor = new Editor({
      element: document.createElement('div'),
      extensions: [
        StarterKit,
        createWidgetEmbedExtension({
          environmentInjector: TestBed.inject(EnvironmentInjector),
          widgetRegistry: REGISTRY,
          blocksById,
          resolveBinding,
          publishOutput,
        }),
      ],
      content: { type: 'doc', content: [{ type: 'paragraph' }, { type: 'widgetEmbed', attrs: { blockId } }] },
    });
    TestBed.tick();
  }

  function button(): HTMLButtonElement | null {
    return editor.view.dom.querySelector('.embed-test-widget');
  }

  it('renders a referenced widget with static props', () => {
    render([widget()]);

    expect(button()?.textContent).toBe('Hello');
    expect(editor.getJSON().content?.[1]).toEqual({ type: 'widgetEmbed', attrs: { blockId: 'widget-1' } });
  });

  it('resolves bound inputs over static props and emits the bound output port', () => {
    resolveBinding.mockReturnValue('Bound heading');
    render([widget({ inputBindings: { label: 'heading' }, outputBindings: { clicked: 'selection' } })]);

    expect(resolveBinding).toHaveBeenCalledWith('heading');
    expect(button()?.textContent).toBe('Bound heading');
    button()?.click();
    expect(publishOutput).toHaveBeenCalledExactlyOnceWith({ widgetId: 'widget-1', port: 'selection', value: 'Bound heading' });
  });

  it('updates props and re-resolves inputs without remounting an unchanged widget', () => {
    render([widget()]);
    const first = button();

    setBlocks([widget({ props: { label: 'Edited' } })]);
    expect(button()).toBe(first);
    expect(button()?.textContent).toBe('Edited');

    resolveBinding.mockReturnValue('New binding');
    setBlocks([widget({ inputBindings: { label: 'heading' } })]);
    expect(button()).toBe(first);
    expect(button()?.textContent).toBe('New binding');
  });

  it('remounts changed output bindings and disconnects the old output', () => {
    const destroyed = vi.spyOn(EmbedTestWidgetComponent.prototype, 'ngOnDestroy');
    render([widget({ outputBindings: { clicked: 'old-port' } })]);
    const first = button();

    setBlocks([widget({ outputBindings: { clicked: 'new-port' } })]);
    expect(button()).not.toBe(first);
    expect(destroyed).toHaveBeenCalledTimes(1);
    first?.click();
    expect(publishOutput).not.toHaveBeenCalled();
    button()?.click();
    expect(publishOutput).toHaveBeenCalledExactlyOnceWith({ widgetId: 'widget-1', port: 'new-port', value: 'Hello' });
  });

  it('remounts when the registered widget type changes', () => {
    const destroyed = vi.spyOn(EmbedTestWidgetComponent.prototype, 'ngOnDestroy');
    render([widget()]);
    const first = button();

    setBlocks([widget({ type: 'other-widget', props: { label: 'Replacement' } })]);

    expect(button()).not.toBe(first);
    expect(button()?.textContent).toBe('Replacement');
    expect(destroyed).toHaveBeenCalledTimes(1);
  });

  it.each([
    { blocks: [], blockId: null, message: 'has no target' },
    { blocks: [], blockId: 'missing', message: "Widget block 'missing' is missing or not REFERENCED." },
    { blocks: [widget({ placement: WidgetPlacement.STANDALONE })], blockId: 'widget-1', message: 'missing or not REFERENCED' },
    { blocks: [widget({ type: 'unknown' })], blockId: 'widget-1', message: "No widget registered for type 'unknown'." },
    { blocks: [widget({ type: undefined })], blockId: 'widget-1', message: "No widget registered for type 'undefined'." },
  ])('shows a repairable placeholder for $message', ({ blocks, blockId, message }) => {
    render(blocks, blockId);

    expect(button()).toBeNull();
    expect(editor.view.dom.querySelector('.pp-widget-embed-broken')?.textContent).toContain(message);
  });

  it('destroys a deleted widget and recovers when its referenced block returns', () => {
    const destroyed = vi.spyOn(EmbedTestWidgetComponent.prototype, 'ngOnDestroy');
    render([widget()]);

    setBlocks([]);
    expect(button()).toBeNull();
    expect(destroyed).toHaveBeenCalledTimes(1);
    expect(editor.view.dom.querySelector('.pp-widget-embed-broken')?.textContent).toContain('missing or not REFERENCED');

    setBlocks([widget()]);
    expect(button()?.textContent).toBe('Hello');
    expect(editor.view.dom.querySelector('.pp-widget-embed-broken')).toBeNull();
  });

  it('selects and deselects the atom node and replaces a changed block reference', () => {
    render([widget(), widget({ id: 'widget-2', props: { label: 'Second' } })]);
    const first = button();

    editor.commands.setNodeSelection(2);
    expect(editor.view.dom.querySelector('.pp-widget-embed--selected')).not.toBeNull();
    editor.commands.updateAttributes('widgetEmbed', { blockId: 'widget-2' });
    TestBed.tick();
    expect(button()).not.toBe(first);
    expect(button()?.textContent).toBe('Second');

    editor.commands.setTextSelection(1);
    expect(editor.view.dom.querySelector('.pp-widget-embed--selected')).toBeNull();
  });

  it('round-trips the block reference through HTML without serializing the live component', () => {
    render([widget()]);
    const html = editor.getHTML();

    expect(html).toContain('data-widget-embed-block-id="widget-1"');
    expect(html).not.toContain('embed-test-widget');
    editor.commands.setContent(html);
    TestBed.tick();
    expect(editor.getJSON().content?.[1]).toEqual({ type: 'widgetEmbed', attrs: { blockId: 'widget-1' } });
    expect(button()?.textContent).toBe('Hello');
  });

  it('destroys the component and its reactive effect with the editor', () => {
    const destroyed = vi.spyOn(EmbedTestWidgetComponent.prototype, 'ngOnDestroy');
    render([widget()]);
    editor.destroy();

    setBlocks([widget({ props: { label: 'Too late' } })]);

    expect(destroyed).toHaveBeenCalledTimes(1);
  });
});
