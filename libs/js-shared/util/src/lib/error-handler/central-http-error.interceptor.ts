import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { ErrorHandler, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

/**
 * Statuses the caller of a request expects and handles itself, so they are not reported as errors.
 *
 * For an answer that is a normal state rather than a failure: an application that has never been
 * published answers its published read with 404, and that is the state of every new organization —
 * reporting it would show an error message on the first visit to every new application. The error is
 * still rethrown, so the caller sees it either way.
 */
export const EXPECTED_ERROR_STATUSES = new HttpContextToken<readonly number[]>(() => []);

export const centralHttpErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const errorHandler = inject(ErrorHandler);
  const expected = request.context.get(EXPECTED_ERROR_STATUSES);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse && expected.includes(error.status))) errorHandler.handleError(error);
      return throwError(() => error);
    }),
  );
};
