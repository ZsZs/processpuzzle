import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { Editor } from '@tiptap/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { DocumentTextBlockToolbarComponent } from './document-text-block-toolbar.component';

/**
 * A stand-in for Tiptap's Editor rather than a real one: what this component owes its caller is the mapping
 * from a button to a command and from editor state to a lit button, and a real Editor in jsdom would only add
 * a ProseMirror view to assert around. The chain is a Proxy so every `toggleX` records itself without this
 * stub having to enumerate StarterKit's command set.
 */
function editorStub(options: { active?: string[]; canUndo?: boolean; canRedo?: boolean } = {}) {
  const commands: string[] = [];
  const listeners = new Set<() => void>();
  let active = new Set(options.active ?? []);

  const chain: unknown = new Proxy(
    {},
    {
      get:
        (_target, property: string) =>
        (...args: unknown[]) => {
          if (property !== 'focus' && property !== 'run') commands.push(property + (args.length > 0 ? JSON.stringify(args[0]) : ''));
          return property === 'run' ? true : chain;
        },
    },
  );

  const editor = {
    chain: () => chain,
    isActive: (name: string, attributes?: Record<string, unknown>) => active.has(attributes ? name + JSON.stringify(attributes) : name),
    can: () => ({ undo: () => options.canUndo !== false, redo: () => options.canRedo !== false }),
    on: (event: string, callback: () => void) => {
      if (event === 'transaction') listeners.add(callback);
    },
    off: (event: string, callback: () => void) => {
      if (event === 'transaction') listeners.delete(callback);
    },
  } as unknown as Editor;

  return {
    editor,
    commands,
    /** Moves the caret, as far as this component can tell — a new active set plus the transaction it arrives on. */
    moveCaret(nextActive: string[]) {
      active = new Set(nextActive);
      listeners.forEach((listener) => listener());
    },
  };
}

describe('DocumentTextBlockToolbarComponent', () => {
  let fixture: ComponentFixture<DocumentTextBlockToolbarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
      providers: [provideTranslocoTesting({ translations: { en: { 'base_document.document.content.toolbar.bold': 'Bold', 'base_document.document.content.toolbar.undo': 'Undo' } } })],
    });
    fixture = TestBed.createComponent(DocumentTextBlockToolbarComponent);
  });

  function button(name: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`[data-testid="document-text-block-${name}"]`);
  }

  function render(stub: ReturnType<typeof editorStub>) {
    fixture.componentRef.setInput('editor', stub.editor);
    fixture.detectChanges();
  }

  it('offers a button for every StarterKit command it claims to expose', () => {
    render(editorStub());

    for (const name of ['bold', 'italic', 'strike', 'code', 'heading-1', 'heading-2', 'heading-3', 'bullet-list', 'ordered-list', 'blockquote', 'horizontal-rule', 'undo', 'redo']) {
      expect(button(name), name).not.toBeNull();
    }
  });

  it('runs the command of the button that was pressed', () => {
    const stub = editorStub();
    render(stub);

    button('bold').click();
    button('heading-2').click();

    expect(stub.commands).toEqual(['toggleBold', 'toggleHeading{"level":2}']);
  });

  /**
   * Without this the command would run against a blurred, collapsed selection and the caret would be lost on
   * every click — the reason each button cancels its own mousedown.
   */
  it('does not let a button press move focus out of the editor', () => {
    render(editorStub());

    const mousedown = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
    button('bold').dispatchEvent(mousedown);

    expect(mousedown.defaultPrevented).toBe(true);
  });

  it('lights the buttons that describe the caret, and only those', () => {
    render(editorStub({ active: ['bold', 'heading{"level":1}'] }));

    expect(button('bold').getAttribute('aria-pressed')).toBe('true');
    expect(button('heading-1').getAttribute('aria-pressed')).toBe('true');
    expect(button('italic').getAttribute('aria-pressed')).toBe('false');
  });

  /**
   * The point of the transaction subscription: Tiptap's state lives outside Angular, so without a signal to
   * depend on the lit buttons would freeze at whatever the caret was on when the toolbar first rendered.
   */
  it('follows the caret as it moves', () => {
    const stub = editorStub({ active: ['bold'] });
    render(stub);

    stub.moveCaret(['italic']);
    fixture.detectChanges();

    expect(button('bold').getAttribute('aria-pressed')).toBe('false');
    expect(button('italic').getAttribute('aria-pressed')).toBe('true');
  });

  /** History buttons are the only ones with an availability of their own; the rest are always offered. */
  it('disables undo and redo when there is no history to move through', () => {
    render(editorStub({ canUndo: false, canRedo: false }));

    expect(button('undo').disabled).toBe(true);
    expect(button('redo').disabled).toBe(true);
    expect(button('bold').disabled).toBe(false);
  });
});
