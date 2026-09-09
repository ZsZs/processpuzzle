import { afterEach, describe, expect, it, vi } from 'vitest';
import { GlobalErrorHandler } from './global-error-handler';

describe('GlobalErrorHandler', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reloads after a failed lazy chunk and always reports the error', () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => undefined);
    const error = new Error('Loading chunk 42 failed');
    const report = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    new GlobalErrorHandler().handleError(error);

    expect(reload).toHaveBeenCalledOnce();
    expect(report).toHaveBeenCalledWith(error);
  });

  it('reports ordinary errors without reloading the application', () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => undefined);
    const report = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    new GlobalErrorHandler().handleError('network unavailable');

    expect(reload).not.toHaveBeenCalled();
    expect(report).toHaveBeenCalledWith('network unavailable');
  });
});
