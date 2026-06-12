import { HttpInterceptorFn } from '@angular/common/http';
import { ErrorHandler, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

export const centralHttpErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const errorHandler = inject(ErrorHandler);

  return next(request).pipe(
    catchError((error: unknown) => {
      errorHandler.handleError(error);
      return throwError(() => error);
    }),
  );
};
