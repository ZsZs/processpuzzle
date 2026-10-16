import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { Editor, Extension } from '@tiptap/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { EMPTY_RICH_TEXT, isRichTextEmpty, RichTextContent, RichTextEditorComponent } from './rich-text-editor.component';

function doc(text: string): RichTextContent {
  return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] };
}

describe('isRichTextEmpty', () => {
  it.each([
    ['nothing', undefined],
    ['an empty document', { type: 'doc' }],
    ['one empty paragraph', EMPTY_RICH_TEXT],
    ['whitespace only', doc('   ')],
  ])('holds for %s', (_case, content) => {
    expect(isRichTextEmpty(content)).toBe(true);
  });

  it.each([
    ['text', doc('Hi')],
    ['a horizontal rule', { type: 'doc', content: [{ type: 'horizontalRule' }] }],
  ])('does not hold for %s', (_case, content) => {
    expect(isRichTextEmpty(content)).toBe(false);
  });
});

describe('RichTextEditorComponent', () => {
  let fixture: ComponentFixture<RichTextEditorComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslocoTesting({ translations: {} })] });
    fixture = TestBed.createComponent(RichTextEditorComponent);
  });

  function editor(): Editor {
    return (fixture.componentInstance as unknown as { editorInstance: () => Editor }).editorInstance();
  }

  function element(): HTMLElement {
    return fixture.nativeElement;
  }

  it('renders its content in an editable editor', () => {
    fixture.componentRef.setInput('content', doc('Hello'));
    fixture.detectChanges();

    expect(element().querySelector('[contenteditable="true"]')?.textContent).toBe('Hello');
  });

  it('renders read-only, without toolbar, when not editable', () => {
    fixture.componentRef.setInput('content', doc('Frozen'));
    fixture.componentRef.setInput('editable', false);
    fixture.detectChanges();

    expect(element().querySelector('[contenteditable="false"]')?.textContent).toBe('Frozen');
    expect(element().classList).toContain('pp-rich-text-editor--readonly');
    editor().commands.focus();
    fixture.detectChanges();
    expect(element().querySelector('pp-rich-text-toolbar')).toBeNull();
  });

  it('shows the toolbar, under its own test-id prefix, while editable', () => {
    fixture.componentRef.setInput('testIdPrefix', 'note');
    fixture.detectChanges();
    expect(element().querySelector('[data-testid="note-toolbar"]')).not.toBeNull();

    fixture.componentRef.setInput('editable', false);
    fixture.detectChanges();
    expect(element().querySelector('[data-testid="note-toolbar"]')).toBeNull();
  });

  it('emits the document as JSON on every edit', () => {
    const emitted: RichTextContent[] = [];
    fixture.componentInstance.contentChange.subscribe((content) => emitted.push(content));
    fixture.detectChanges();

    editor().commands.insertContent('Typed');

    expect(emitted.at(-1)).toEqual(doc('Typed'));
  });

  it('shows its placeholder while empty, and hides it once typed into', () => {
    fixture.componentRef.setInput('placeholder', 'Say something');
    fixture.detectChanges();
    expect(element().querySelector('.pp-rich-text-editor__placeholder')?.textContent).toBe('Say something');

    editor().commands.insertContent('Something');
    fixture.detectChanges();
    expect(element().querySelector('.pp-rich-text-editor__placeholder')).toBeNull();
  });

  it('follows a new content value without echoing it back', () => {
    const emitted: RichTextContent[] = [];
    fixture.componentInstance.contentChange.subscribe((content) => emitted.push(content));
    fixture.componentRef.setInput('content', doc('Before'));
    fixture.detectChanges();

    fixture.componentRef.setInput('content', doc('After'));
    fixture.detectChanges();

    expect(editor().getJSON()).toEqual(doc('After'));
    expect(emitted).toEqual([]);
  });

  it('adds the extensions it is given to StarterKit', () => {
    fixture.componentRef.setInput('extensions', [Extension.create({ name: 'probe' })]);
    fixture.detectChanges();

    expect(editor().extensionManager.extensions.map((extension) => extension.name)).toEqual(expect.arrayContaining(['starterKit', 'probe']));
  });

  it('destroys its editor with itself', () => {
    fixture.detectChanges();
    const instance = editor();

    fixture.destroy();

    expect(instance.isDestroyed).toBe(true);
  });
});
