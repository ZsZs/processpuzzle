import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom, Observable } from 'rxjs';

export interface SelectableRole {
  readonly name: string;
  readonly description?: string;
}

/** The caller's realm roles, and the ones they may pick for themselves. */
export interface SelfServiceRoles {
  readonly assigned: readonly string[];
  readonly selectable: readonly SelectableRole[];
}

/** A refused role call; `code` is the backend's `errorId`, e.g. `role.unknown`. */
export class SelfServiceRolesError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'SelfServiceRolesError';
  }
}

/**
 * Lets the signed-in user pick realm roles for themselves, through `GET` / `PUT {root}/me/roles`. The
 * backend acts on the token's own subject and offers no administrative roles, so the browser never
 * names a user.
 *
 * `SELF_SERVICE_ROLES_SERVICE_ROOT` is an organization-scoped root, like `BACKEND_SERVICE_ROOT`, and
 * it is the switch: without it {@link available} is false and My Profile stays read-only. It
 * deliberately has no fallback — an application that leaves role assignment to administrators
 * must not grow the feature just because it has a backend root.
 */
@Injectable({ providedIn: 'root' })
export class SelfServiceRolesService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  get available(): boolean {
    return this.serviceRoot() !== '';
  }

  async load(): Promise<SelfServiceRoles> {
    return this.call(() => this.http.get<SelfServiceRoles>(this.url()));
  }

  async replace(roles: readonly string[]): Promise<SelfServiceRoles> {
    return this.call(() => this.http.put<SelfServiceRoles>(this.url(), { roles }));
  }

  private async call(request: () => Observable<SelfServiceRoles>): Promise<SelfServiceRoles> {
    try {
      return await firstValueFrom(request());
    } catch (error: unknown) {
      throw toSelfServiceRolesError(error);
    }
  }

  private url(): string {
    return `${this.serviceRoot()}/me/roles`;
  }

  private serviceRoot(): string {
    return organizationRoot(this.runtimeConfiguration);
  }
}

/** The organization-scoped `SELF_SERVICE_ROLES_SERVICE_ROOT`, without a trailing slash; empty when not configured. */
export function organizationRoot(runtimeConfiguration: object | null): string {
  const base = runtimeConfiguration ? Reflect.get(runtimeConfiguration, 'BASE_CONFIGURATION') : undefined;
  const root: string = (base && Reflect.get(base, 'SELF_SERVICE_ROLES_SERVICE_ROOT')) ?? '';
  return root.endsWith('/') ? root.slice(0, -1) : root;
}

function toSelfServiceRolesError(error: unknown): SelfServiceRolesError {
  if (error instanceof HttpErrorResponse) {
    const fallback = error.status === 0 ? 'self-service-roles.unreachable' : `http.${error.status}`;
    const code = typeof error.error?.errorId === 'string' ? error.error.errorId : fallback;
    return new SelfServiceRolesError(code, error.error?.errorText ?? error.message, error.status);
  }
  return new SelfServiceRolesError('self-service-roles.unknown', error instanceof Error ? error.message : 'unknown');
}
