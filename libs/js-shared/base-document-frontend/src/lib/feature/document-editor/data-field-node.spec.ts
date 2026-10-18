import { EnvironmentInjector, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createDataFieldExtension, DATA_FIELD_NODE } from './data-field-node';

function doc(path: string, format = 'text') {
  return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Due: ' }, { type: DATA_FIELD_NODE, attrs: { path, format } }] }] };
}

describe('dataField node', () => {
  let editor: Editor;
  const values = signal<Record<string, unknown>>({});

  beforeEach(() => {
    TestBed.configureTestingModule({});
    values.set({});
  });

  afterEach(() => editor?.destroy());

  function render(content: object, editable: boolean) {
    editor = new Editor({
      element: document.createElement('div'),
      editable,
      content,
      extensions: [
        StarterKit,
        createDataFieldExtension({ environmentInjector: TestBed.inject(EnvironmentInjector), resolveBinding: (path) => values()[path], locale: () => 'en', editable }),
      ],
    });
    TestBed.tick();
    return editor.view.dom.querySelector('.pp-data-field') as HTMLElement;
  }

  it('shows the path it names while authoring, and the value once one resolves', () => {
    const field = render(doc('order.total', 'currency:EUR'), true);

    expect(field.textContent).toBe('{order.total}');
    expect(field.classList).toContain('pp-data-field--unresolved');

    values.set({ 'order.total': 12.5 });
    TestBed.tick();

    expect(field.textContent).toBe('€12.50');
    expect(field.classList).not.toContain('pp-data-field--unresolved');
  });

  it('renders nothing for an unresolved field when read', () => {
    const field = render(doc('order.total'), false);

    expect(field.textContent).toBe('');
    expect(field.classList).not.toContain('pp-data-field--unresolved');
  });

  it('round-trips through its JSON and HTML forms', () => {
    render(doc('order.number'), true);

    expect(editor.getJSON().content?.[0].content?.[1]).toEqual({ type: DATA_FIELD_NODE, attrs: { path: 'order.number', format: 'text' } });
    expect(editor.getHTML()).toContain('<span data-field-path="order.number" data-field-format="text">{order.number}</span>');

    editor.commands.setContent('<p><span data-field-path="order.date" data-field-format="date">x</span><span data-field-path="order.y">y</span></p>');
    const fields = editor.getJSON().content?.[0].content;
    expect(fields?.[0]).toEqual({ type: DATA_FIELD_NODE, attrs: { path: 'order.date', format: 'date' } });
    expect(fields?.[1]).toEqual({ type: DATA_FIELD_NODE, attrs: { path: 'order.y', format: 'text' } });
  });

  it('stops following its value once destroyed', () => {
    const field = render(doc('order.total'), true);
    editor.commands.setContent({ type: 'doc', content: [{ type: 'paragraph' }] });
    values.set({ 'order.total': 1 });
    TestBed.tick();

    expect(field.textContent).toBe('{order.total}');
  });
});
