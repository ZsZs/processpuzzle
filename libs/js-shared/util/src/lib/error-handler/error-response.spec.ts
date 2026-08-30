import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { formatHttpError, httpErrorBodyMessage, httpErrorId, httpErrorMessage, isErrorResponse } from './error-response';

const httpError = (error: unknown, status = 400, url = '/api/organizations/acme/documents') => new HttpErrorResponse({ error, status, url });

/** What both backends actually send, per `ErrorResponse` in `shared-api.yaml`. */
const anErrorResponse = { errorId: 'document.slug.already-exists', errorText: "A document with slug 'q3-plan' already exists." };

describe('isErrorResponse', () => {
  it('accepts the contract body', () => {
    expect(isErrorResponse(anErrorResponse)).toBe(true);
  });

  it('rejects a body missing either half, because both are required', () => {
    expect(isErrorResponse({ errorId: 'document.not-found' })).toBe(false);
    expect(isErrorResponse({ errorText: 'Not found.' })).toBe(false);
  });

  it('rejects the shapes the old backends sent, so nothing mistakes them for the new one', () => {
    expect(isErrorResponse({ error: 'orgKey must not be blank' })).toBe(false);
    expect(isErrorResponse({ slug: 'must not be blank' })).toBe(false);
    expect(isErrorResponse({ timestamp: 0, status: 500, error: 'Internal Server Error', path: '/api' })).toBe(false);
  });

  it('rejects non-objects rather than throwing on them', () => {
    expect(isErrorResponse(null)).toBe(false);
    expect(isErrorResponse(undefined)).toBe(false);
    expect(isErrorResponse('errorId')).toBe(false);
    expect(isErrorResponse(42)).toBe(false);
  });

  it('rejects an errorId that is not a string, which is what a JSON number would arrive as', () => {
    expect(isErrorResponse({ errorId: 409, errorText: 'Conflict.' })).toBe(false);
  });
});

describe('httpErrorId', () => {
  it('reads the id off one of our bodies, which is the point of having a stable id', () => {
    expect(httpErrorId(httpError(anErrorResponse, 409))).toBe('document.slug.already-exists');
  });

  it('answers undefined for a foreign body, so a caller cannot branch on a fabricated id', () => {
    expect(httpErrorId(httpError({ message: 'Firebase: Error (auth/user-not-found).' }))).toBeUndefined();
    expect(httpErrorId(httpError('Bad Gateway', 502))).toBeUndefined();
  });

  it('answers undefined for anything that is not an HTTP failure', () => {
    expect(httpErrorId(new Error('boom'))).toBeUndefined();
    expect(httpErrorId(anErrorResponse)).toBeUndefined();
  });
});

describe('httpErrorMessage', () => {
  it('prefers errorText — the case the whole helper exists for', () => {
    expect(httpErrorMessage(httpError(anErrorResponse, 409))).toBe("A document with slug 'q3-plan' already exists.");
  });

  /**
   * The regression this guards: `HttpErrorResponse.message` is always populated, so a reader that looked
   * at it — or at a `message` key no backend sends — replaced every server message with this string.
   */
  it('does not fall back to Angular synthesized text when the server said something', () => {
    expect(httpErrorMessage(httpError(anErrorResponse, 409))).not.toContain('Http failure response');
  });

  it('tolerates a message key, because errors from things that are not ours use it', () => {
    expect(httpErrorMessage(httpError({ message: 'Firebase: Error (auth/user-not-found).' }, 401))).toBe('Firebase: Error (auth/user-not-found).');
  });

  it('takes a plain string body, which is what a proxy or a route stub returns', () => {
    expect(httpErrorMessage(httpError('Bad Gateway', 502))).toBe('Bad Gateway');
  });

  it('ignores an empty message and an empty string body rather than displaying nothing', () => {
    expect(httpErrorMessage(httpError({ message: '' }))).toContain('Http failure response');
    expect(httpErrorMessage(httpError(''))).toContain('Http failure response');
  });

  it('falls back to the Angular message when the body carries none', () => {
    expect(httpErrorMessage(httpError(null, 500))).toContain('Http failure response');
  });

  it('describes a transport failure, whose status is 0 and whose body is a ProgressEvent', () => {
    const offline = new HttpErrorResponse({ error: new ProgressEvent('error'), status: 0, url: '/api/documents' });

    expect(httpErrorMessage(offline)).toBeTruthy();
  });

  it('uses an Error message, so stores can hand it any thrown value', () => {
    expect(httpErrorMessage(new Error('repository unreachable'))).toBe('repository unreachable');
  });

  it('never answers an empty string, which would render a blank snackbar', () => {
    expect(httpErrorMessage(new Error(''))).toBe('Unhandled exception.');
    expect(httpErrorMessage(42)).toBe('Unhandled exception.');
    expect(httpErrorMessage(undefined)).toBe('Unhandled exception.');
  });

  it('reads a bare body that was never wrapped in an HttpErrorResponse', () => {
    expect(httpErrorMessage(anErrorResponse)).toBe("A document with slug 'q3-plan' already exists.");
    expect(httpErrorMessage({ message: 'plain object failure' })).toBe('plain object failure');
  });
});

describe('httpErrorBodyMessage', () => {
  it('answers the server text when there is some', () => {
    expect(httpErrorBodyMessage(httpError(anErrorResponse, 409))).toBe("A document with slug 'q3-plan' already exists.");
  });

  it('answers undefined when the server sent no body, which is how the log line stays free of duplication', () => {
    expect(httpErrorBodyMessage(httpError(null, 500))).toBeUndefined();
  });

  it('answers undefined for a local Error, whose message did not come from a server', () => {
    expect(httpErrorBodyMessage(new Error('boom'))).toBeUndefined();
    expect(httpErrorBodyMessage(anErrorResponse)).toBeUndefined();
  });
});

describe('formatHttpError', () => {
  it('names the status and the URL', () => {
    expect(formatHttpError(httpError(null, 503, '/api/workflows'))).toBe('HTTP 503 /api/workflows');
  });

  it('omits the URL when there is none, rather than printing a trailing space', () => {
    expect(formatHttpError(new HttpErrorResponse({ status: 503 }))).toBe('HTTP 503');
  });
});
