import { afterEach, describe, expect, it, vi } from 'vitest';
import { printElement } from './print-element';

describe('printElement', () => {
  afterEach(() => {
    vi.useRealTimers();
    document.querySelectorAll('iframe').forEach((frame) => frame.remove());
    document.head.querySelectorAll('style[data-test]').forEach((style) => style.remove());
  });

  it('prints a copy of the element with the page styles in a frame it removes afterwards', () => {
    vi.useFakeTimers();
    const style = document.createElement('style');
    style.setAttribute('data-test', '');
    style.textContent = '.report { color: red; }';
    document.head.appendChild(style);
    const report = document.createElement('div');
    report.className = 'report';
    report.textContent = 'Invoice A-1';

    printElement(report, 'Invoice');

    const frame = document.querySelector('iframe') as HTMLIFrameElement;
    const printed = frame.contentDocument as Document;
    // jsdom's window has no print(); the test supplies the one a browser would.
    const print = vi.fn();
    Object.defineProperty(frame.contentWindow as Window, 'print', { value: print });
    expect(printed.title).toBe('Invoice');
    expect(printed.body.textContent).toBe('Invoice A-1');
    expect(printed.head.textContent).toContain('.report { color: red; }');
    expect(printed.head.textContent).toContain('@page');

    vi.runAllTimers();
    expect(print).toHaveBeenCalled();

    frame.contentWindow?.dispatchEvent(new Event('afterprint'));
    expect(document.querySelector('iframe')).toBeNull();
  });

  it('gives up quietly when the frame has no document', () => {
    const original = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const element = original(tag);
      if (tag === 'iframe') Object.defineProperty(element, 'contentDocument', { value: null });
      return element;
    });

    printElement(document.createElement('div'), 'x');

    expect(document.querySelector('iframe')).toBeNull();
    vi.restoreAllMocks();
  });
});
