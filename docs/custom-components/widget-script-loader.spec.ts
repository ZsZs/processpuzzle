// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { clearScriptCache, loadScriptOnce } from './widget-script-loader';

const URL_A = 'https://cdn.example.com/race-timer/1.2.0/main.js';

describe('loadScriptOnce', () => {
  beforeEach(() => {
    clearScriptCache();
    document.head.innerHTML = '';
  });

  it('appends one module script with SRI and crossorigin, shared by concurrent callers', async () => {
    const first = loadScriptOnce(URL_A, 'sha384-abc');
    const second = loadScriptOnce(URL_A, 'sha384-abc');
    expect(second).toBe(first);

    const scripts = document.head.querySelectorAll('script');
    expect(scripts).toHaveLength(1);
    expect(scripts[0].getAttribute('type')).toBe('module');
    expect(scripts[0].getAttribute('src')).toBe(URL_A);
    expect(scripts[0].getAttribute('integrity')).toBe('sha384-abc');
    expect(scripts[0].getAttribute('crossorigin')).toBe('anonymous');

    scripts[0].dispatchEvent(new Event('load'));
    await expect(first).resolves.toBeUndefined();
  });

  it('does not add a second script after a successful load', async () => {
    const first = loadScriptOnce(URL_A);
    document.head.querySelector('script')?.dispatchEvent(new Event('load'));
    await first;
    await loadScriptOnce(URL_A);
    expect(document.head.querySelectorAll('script')).toHaveLength(1);
  });

  it('omits the integrity attribute when none is given', () => {
    void loadScriptOnce(URL_A);
    expect(document.head.querySelector('script')?.hasAttribute('integrity')).toBe(false);
  });

  it('removes the script and allows a retry after an error', async () => {
    const failing = loadScriptOnce(URL_A);
    document.head.querySelector('script')?.dispatchEvent(new Event('error'));
    await expect(failing).rejects.toThrow('Failed to load widget script');
    expect(document.head.querySelectorAll('script')).toHaveLength(0);

    const retry = loadScriptOnce(URL_A);
    expect(retry).not.toBe(failing);
    expect(document.head.querySelectorAll('script')).toHaveLength(1);
    document.head.querySelector('script')?.dispatchEvent(new Event('load'));
    await expect(retry).resolves.toBeUndefined();
  });
});
