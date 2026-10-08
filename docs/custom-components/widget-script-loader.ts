/**
 * Loads a widget bundle by URL. Framework-free.
 *
 * Behaviour:
 * - one <script type="module"> per URL, concurrent callers share the same promise
 * - `integrity` (SRI) and `crossorigin="anonymous"` are set so the browser refuses a tampered bundle
 * - a failed load removes the script tag and the cache entry, so a later call can retry
 *
 * The cache key is the URL only. A second call with a different integrity value for an
 * already-loaded URL does not reload the script; the host should treat (entry, integrity)
 * as part of the registered manifest and never vary it per call.
 */
export type ScriptLoader = (entry: string, integrity?: string) => Promise<void>;

const pending = new Map<string, Promise<void>>();

export function loadScriptOnce(
  entry: string,
  integrity?: string,
  doc: Document = document,
): Promise<void> {
  const existing = pending.get(entry);
  if (existing) return existing;

  const promise = new Promise<void>((resolve, reject) => {
    const script = doc.createElement('script');
    script.setAttribute('type', 'module');
    script.setAttribute('src', entry);
    script.setAttribute('crossorigin', 'anonymous');
    if (integrity) script.setAttribute('integrity', integrity);

    script.addEventListener('load', () => resolve(), { once: true });
    script.addEventListener(
      'error',
      () => {
        script.remove();
        pending.delete(entry);
        reject(new Error(`Failed to load widget script '${entry}'`));
      },
      { once: true },
    );
    doc.head.appendChild(script);
  });

  pending.set(entry, promise);
  return promise;
}

/** Forgets all cached loads. Intended for tests. */
export function clearScriptCache(): void {
  pending.clear();
}
