import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';

export interface RegistrationRequest {
  readonly email: string;
  readonly password: string;
  readonly firstName?: string;
  readonly lastName?: string;
}

/** A refused registration; `code` is the backend's `errorId`, e.g. `user.already-exists`. */
export class RegistrationError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'RegistrationError';
  }
}

/**
 * Self-registration through the ProcessPuzzle backend, which creates the account in Keycloak with its
 * Admin API. The browser never talks to Keycloak's own registration page, and the password goes to
 * the backend over the same origin the application already uses.
 *
 * The endpoint is `POST {REGISTRATION_SERVICE_ROOT}/registration`. `REGISTRATION_SERVICE_ROOT` is the
 * server root rather than a tenant root, because registration happens before there is a user and
 * the path is not under `/organizations/{orgKey}`; it falls back to `OBJECT_STORE_SERVICE_ROOT`,
 * which already is that root in every deployment.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  async register(request: RegistrationRequest): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>(`${this.serviceRoot()}/registration`, request));
    } catch (error: unknown) {
      throw toRegistrationError(error);
    }
  }

  private serviceRoot(): string {
    const base = this.runtimeConfiguration ? Reflect.get(this.runtimeConfiguration, 'BASE_CONFIGURATION') : undefined;
    const root: string = (base && (Reflect.get(base, 'REGISTRATION_SERVICE_ROOT') ?? Reflect.get(base, 'OBJECT_STORE_SERVICE_ROOT'))) ?? '';
    return root.endsWith('/') ? root.slice(0, -1) : root;
  }
}

function toRegistrationError(error: unknown): RegistrationError {
  if (error instanceof HttpErrorResponse) {
    const fallback = error.status === 0 ? 'registration.unreachable' : `http.${error.status}`;
    const code = typeof error.error?.errorId === 'string' ? error.error.errorId : fallback;
    return new RegistrationError(code, error.error?.errorText ?? error.message, error.status);
  }
  return new RegistrationError('registration.unknown', error instanceof Error ? error.message : 'unknown');
}
