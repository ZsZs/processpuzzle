import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ERROR_MESSAGE_REPORTER } from '@processpuzzle/util';
import { AUTHENTICATION_SERVICE } from './provide-authentication.service';
import { AuthService } from './auth.service';
import { providePlatformHttp, PlatformHttpOptions } from './provide-platform-http';
import { SESSION_EXPIRED_REDIRECT_KEY } from './session-expired.interceptor';
import { User } from '../user/user';

class StubAuthService extends AuthService {
  login = vi.fn(async (_redirectUrl?: string): Promise<User | undefined> => undefined);

  constructor(signedIn: boolean) {
    super();
    if (signedIn) this._user.set(new User('a@b.c', '1', 'A', 'B', null, []));
  }

  override async authenticate(): Promise<boolean> {
    return !!this.getCurrentUser();
  }

  override async logout(): Promise<void> {
    // nothing to do
  }

  override getCurrentUser(): User | undefined {
    return this.currentUser();
  }

  override getAccessToken(): string | undefined {
    return this.getCurrentUser() ? 'tok' : undefined;
  }

  override clearSession(): void {
    this._user.set(undefined);
  }
}

/** The token interceptor refreshes first, which is asynchronous, so the request reaches the backend a moment after the call. */
const reached = (controller: HttpTestingController, url: string) => vi.waitFor(() => controller.expectOne(url));

describe('providePlatformHttp', () => {
  const reporter = { showErrorMessage: vi.fn() };
  const router = { url: '/', navigateByUrl: vi.fn(), createUrlTree: vi.fn() };

  function configure(signedIn: boolean, options: PlatformHttpOptions = {}) {
    const auth = new StubAuthService(signedIn);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        providePlatformHttp(options),
        provideHttpClientTesting(),
        { provide: AUTHENTICATION_SERVICE, useValue: auth },
        { provide: ERROR_MESSAGE_REPORTER, useValue: reporter },
        { provide: Router, useValue: router },
      ],
    });
    return { auth, http: TestBed.inject(HttpClient), controller: TestBed.inject(HttpTestingController) };
  }

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('attaches the token to the platform API and leaves a third-party URL without one', async () => {
    const { http, controller } = configure(true, { apiRoots: ['http://localhost:8080', undefined] });

    const own = firstValueFrom(http.get('http://localhost:8080/api/x'));
    const ownRequest = await reached(controller, 'http://localhost:8080/api/x');
    expect(ownRequest.request.headers.get('Authorization')).toBe('Bearer tok');
    ownRequest.flush({});
    await own;

    const foreign = firstValueFrom(http.get('https://analytics.example/collect'));
    const foreignRequest = await reached(controller, 'https://analytics.example/collect');
    expect(foreignRequest.request.headers.has('Authorization')).toBe(false);
    foreignRequest.flush({});
    await foreign;
  });

  it('runs the standard chain in a fixed order, with the application interceptors at either end', async () => {
    const order: string[] = [];
    const tracer =
      (name: string): HttpInterceptorFn =>
      (request, next) => {
        order.push(`${name}:${request.headers.get('Authorization') ?? 'no-token'}`);
        return next(request);
      };
    const { http, controller } = configure(true, { leadingInterceptors: [tracer('leading')], trailingInterceptors: [tracer('trailing')] });

    const result = firstValueFrom(http.get('/api/x'));
    (await reached(controller, '/api/x')).flush({});
    await result;

    // Leading runs before the token is attached, trailing after it.
    expect(order).toEqual(['leading:no-token', 'trailing:Bearer tok']);
  });

  it('sends a signed-in user to log in when the API answers 401, and still fails the call', async () => {
    const { auth, http, controller } = configure(true);

    const result = firstValueFrom(http.get('/api/x'));
    (await reached(controller, '/api/x')).flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY)).not.toBeNull();
  });

  // The reason Biz can use the same chain as every other application: its public pages are read by visitors who never
  // signed in, and a 401 for one of them must not become a login.
  it('leaves an anonymous visitor alone: the 401 propagates, with no login and no guidance page', async () => {
    const { auth, http, controller } = configure(false);

    const result = firstValueFrom(http.get('/api/public-page'));
    (await reached(controller, '/api/public-page')).flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(auth.login).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY)).toBeNull();
  });

  it('does not treat a Keycloak 401 as a session ending', async () => {
    const { auth, http, controller } = configure(true);

    const result = firstValueFrom(http.post('/realms/x/protocol/openid-connect/token', {}));
    (await reached(controller, '/realms/x/protocol/openid-connect/token')).flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(auth.login).not.toHaveBeenCalled();
  });
});
