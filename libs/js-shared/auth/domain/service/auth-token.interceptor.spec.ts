import { describe, expect, it, vi } from 'vitest';
import { HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { authTokenInterceptor } from './auth-token.interceptor';
import { AUTHENTICATION_SERVICE } from './provide-authentication.service';
import { AuthService } from './auth.service';
import { User } from '../user/user';

class StubAuthService extends AuthService {
  refreshed = 0;

  constructor(private readonly token: string | undefined) {
    super();
  }

  override async authenticate(): Promise<boolean> {
    return true;
  }

  override async login(): Promise<User | undefined> {
    return undefined;
  }

  override async logout(): Promise<void> {
    // nothing to do
  }

  override getCurrentUser(): User | undefined {
    return undefined;
  }

  override getAccessToken(): string | undefined {
    return this.token;
  }

  override async refreshAccessToken(): Promise<boolean> {
    this.refreshed += 1;
    return true;
  }
}

/**
 * Runs the interceptor inside an injection context and reports the request that reached `next`.
 *
 * Resets the TestBed first, because a test that calls this twice would otherwise keep the first
 * call's providers — `configureTestingModule` is a no-op once the injector has been created, so the
 * second stub would be silently ignored and the assertion would pass or fail for the wrong reason.
 */
async function intercept(url: string, authService: AuthService | null, apiRoots: string[] = []): Promise<HttpRequest<unknown>> {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: authService ? [{ provide: AUTHENTICATION_SERVICE, useValue: authService }] : [],
  });

  const request = new HttpRequest<unknown>('GET', url);
  const next = vi.fn((forwarded: HttpRequest<unknown>) => of(forwarded));

  await TestBed.runInInjectionContext(() => firstValueFrom(authTokenInterceptor(apiRoots)(request, next as never) as never));

  return next.mock.calls[0][0];
}

describe('authTokenInterceptor', () => {
  it('attaches the bearer token to a relative URL, which is what BaseEntityRestService produces', async () => {
    const forwarded = await intercept('/api/organizations/my-org/admin/users', new StubAuthService('tok'));

    expect(forwarded.headers.get('Authorization')).toBe('Bearer tok');
  });

  it('attaches the token to a same-origin absolute URL', async () => {
    const forwarded = await intercept(`${window.location.origin}/api/rules`, new StubAuthService('tok'));

    expect(forwarded.headers.get('Authorization')).toBe('Bearer tok');
  });

  // The security property of this interceptor: a bearer token is a credential for this platform, so
  // sending it to an arbitrary host hands that host a working token.
  it('withholds the token from a third-party origin', async () => {
    const forwarded = await intercept('https://analytics.example/collect', new StubAuthService('tok'));

    expect(forwarded.headers.has('Authorization')).toBe(false);
  });

  it('attaches the token to a cross-origin URL only when it was named as an API root', async () => {
    const withoutRoot = await intercept('http://localhost:8080/api/rules', new StubAuthService('tok'));
    const withRoot = await intercept('http://localhost:8080/api/rules', new StubAuthService('tok'), ['http://localhost:8080']);

    expect(withoutRoot.headers.has('Authorization')).toBe(false);
    expect(withRoot.headers.get('Authorization')).toBe('Bearer tok');
  });

  // Keycloak's token endpoint authenticates with the refresh token in the body; an Authorization
  // header there is ignored at best and rejected at worst.
  it('never touches the identity provider own endpoints', async () => {
    const forwarded = await intercept('/realms/my-org/protocol/openid-connect/token', new StubAuthService('tok'));

    expect(forwarded.headers.has('Authorization')).toBe(false);
  });

  it('sends the request unchanged when the provider has no token', async () => {
    const forwarded = await intercept('/api/rules', new StubAuthService(undefined));

    expect(forwarded.headers.has('Authorization')).toBe(false);
  });

  it('sends the request unchanged when no auth service is provided at all', async () => {
    const forwarded = await intercept('/api/rules', null);

    expect(forwarded.headers.has('Authorization')).toBe(false);
  });

  // A screen left open past the token's lifetime must not send an expired one.
  it('refreshes before reading the token', async () => {
    const authService = new StubAuthService('tok');

    await intercept('/api/rules', authService);

    expect(authService.refreshed).toBe(1);
  });
});
