import { HttpErrorResponse } from '@angular/common/http';

/**
 * The error body every ProcessPuzzle service returns, as declared by `ErrorResponse` in
 * `shared-api.yaml` and repeated in every feature contract.
 *
 * Both backends emit it: the Spring handlers (`ApiExceptionHandler`, `DocumentApiExceptionHandler`,
 * `RuleApiExceptionHandler`, `AppApiExceptionHandler`) and the Cloud Functions
 * (`tools/firebase/functions/src/base-document`), with the same `errorId` for the same refusal — so a
 * client cannot tell which one served it.
 *
 * This type exists because its absence was the actual defect: the body was handled as `unknown`
 * everywhere, so nothing noticed that the frontend read a `message` key no backend has ever sent, and
 * every server-authored message was silently replaced by "Http failure response for …".
 */
export interface ErrorResponse {
  /**
   * Stable, machine-readable identifier — dotted and namespaced by feature, e.g.
   * `document.slug.already-exists`, `request.invalid-argument`. Safe to branch on, and intended to
   * double as a Transloco key once an `errors.*` namespace exists.
   */
  readonly errorId: string;
  /** Human-readable fallback, in the service's default language. */
  readonly errorText: string;
}

export function isErrorResponse(value: unknown): value is ErrorResponse {
  if (typeof value !== 'object' || value === null) return false;

  const candidate = value as Partial<ErrorResponse>;
  return typeof candidate.errorId === 'string' && typeof candidate.errorText === 'string';
}

/** The `errorId` of an HTTP failure, when the body is one of ours. Undefined for foreign errors. */
export function httpErrorId(error: unknown): string | undefined {
  const body = error instanceof HttpErrorResponse ? error.error : undefined;
  return isErrorResponse(body) ? body.errorId : undefined;
}

/**
 * The best human-readable message available for any thrown value, HTTP or not.
 *
 * Precedence, and why each step is there:
 *  1. `errorText` — our own services, the case that matters.
 *  2. a string `message` on the body — errors from things that are not ours: Firebase Auth, a reverse
 *     proxy, Spring Boot's own `/error` when nothing handled the exception.
 *  3. a plain string body — `text/plain` failures, which is what a proxy or a route stub returns.
 *  4. `HttpErrorResponse.message` — Angular's synthesized "Http failure response for …".
 *  5. `HTTP <status> <url>` — a transport failure has status 0 and an empty message.
 *
 * Steps 4 and 5 are deliberately last: before this existed they were reached for *every* server error,
 * because the reader looked for a key nobody sent.
 */
export function httpErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    return bodyMessage(error.error) ?? error.message ?? formatHttpError(error);
  }
  if (error instanceof Error) {
    return error.message || 'Unhandled exception.';
  }
  return bodyMessage(error) ?? 'Unhandled exception.';
}

/**
 * The message the *server* put in the response body, or undefined when it sent none.
 *
 * Distinct from {@link httpErrorMessage}, which always answers something: this one is for callers that
 * need to know whether there is server-authored text at all. `CentralErrorHandler` uses it to decide
 * whether its log line has anything to add beyond the status and URL — appending `httpErrorMessage`
 * unconditionally would repeat Angular's synthesized "Http failure response for <url>: <status>",
 * which already carries both.
 *
 * Deliberately answers undefined for anything that is not an HTTP failure. A local `Error` also has a
 * `message`, but it did not come from a server, and a caller asking this question is asking about the
 * wire.
 */
export function httpErrorBodyMessage(error: unknown): string | undefined {
  return error instanceof HttpErrorResponse ? bodyMessage(error.error) : undefined;
}

function bodyMessage(body: unknown): string | undefined {
  if (isErrorResponse(body)) return body.errorText;

  if (typeof body === 'object' && body !== null) {
    const message = (body as { message?: unknown }).message;
    if (typeof message === 'string' && message.length > 0) return message;
    return undefined;
  }

  return typeof body === 'string' && body.length > 0 ? body : undefined;
}

export function formatHttpError(error: HttpErrorResponse): string {
  const url = error.url ? ` ${error.url}` : '';
  return `HTTP ${error.status}${url}`;
}
