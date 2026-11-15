import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { ERROR_MESSAGE_REPORTER } from '@processpuzzle/util';
import { AUTHENTICATION_SERVICE } from './provide-authentication.service';
import { AuthService } from './auth.service';
import { SESSION_EXPIRED_LOOP_WINDOW_MS, SESSION_EXPIRED_MESSAGE, SESSION_EXPIRED_REDIRECT_KEY, sessionExpiredInterceptor } from './session-expired.interceptor';
import { LOGIN_REQUIRED, SESSION_EXPIRED_ROUTE } from './session-expired.route';
import { User } from '../user/user';

class StubAuthService extends AuthService {
  login = vi.fn(async (_redirectUrl?: string): Promise<User | undefined> => undefined);
  cleared = 0;

  constructor(signedIn = true) {
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

  // Like Keycloak's: the login must find nobody signed in, or it would return early.
  override clearSession(): void {
    this.cleared += 1;
    this._user.set(undefined);
  }
}

const error = (status: number, url = '/api/things') => new HttpErrorResponse({ status, url });

describe('sessionExpiredInterceptor', () => {
  let reporter: { showErrorMessage: ReturnType<typeof vi.fn> };
  let auth: StubAuthService;
  let clock: number;
  let router: { url: string; navigateByUrl: ReturnType<typeof vi.fn>; createUrlTree: ReturnType<typeof vi.fn> };
  let loginRequired = false;

  function setup(authService: AuthService | null = auth, apiRoots: string[] = [], withheldRoots: string[] = []) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [...(authService ? [{ provide: AUTHENTICATION_SERVICE, useValue: authService }] : []), { provide: ERROR_MESSAGE_REPORTER, useValue: reporter },
        { provide: Router, useValue: router },
        { provide: LOGIN_REQUIRED, useValue: () => loginRequired },
      ],
    });
    const interceptor = sessionExpiredInterceptor(apiRoots, withheldRoots, () => clock);
    return (url: string, outcome: Observable<unknown>) =>
      TestBed.runInInjectionContext(() => interceptor(new HttpRequest<unknown>('GET', url), (() => outcome) as never) as Observable<unknown>);
  }

  beforeEach(() => {
    sessionStorage.clear();
    reporter = { showErrorMessage: vi.fn() };
    loginRequired = false;
    router = { url: '/acme/orders', navigateByUrl: vi.fn(), createUrlTree: vi.fn((commands: string[], extras?: object) => ({ commands, extras })) };
    auth = new StubAuthService();
    clock = 1_000_000;
    window.history.replaceState(null, '', '/acme/orders?page=2');
    // Without a <base href> the document's base is the page itself, which is not how the apps are served.
    document.head.insertAdjacentHTML('beforeend', '<base href="/">');
  });

  afterEach(() => {
    sessionStorage.clear();
    document.head.querySelector('base')?.remove();
  });

  it('clears the session and sends the user to log in, coming back to the page they are on', async () => {
    const call = setup();

    await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(auth.cleared).toBe(1);
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(auth.login).toHaveBeenCalledWith('acme/orders?page=2');
  });

  it('rethrows the very error it saw, so nothing downstream sees a fake success', async () => {
    const original = error(401);
    const call = setup();

    await expect(firstValueFrom(call('/api/things', throwError(() => original)))).rejects.toBe(original);
  });

  it('starts one login for parallel 401s', async () => {
    const call = setup();

    await Promise.allSettled([1, 2, 3, 4].map(() => firstValueFrom(call('/api/things', throwError(() => error(401))))));

    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(auth.cleared).toBe(1);
    expect(reporter.showErrorMessage).not.toHaveBeenCalled();
  });

  it('starts one login for parallel 401s even when the provider cannot clear its user', async () => {
    // The base AuthService.clearSession does nothing, so the signed-in user stays: the interceptor's own flag must dedupe.
    const stubborn = new StubAuthService();
    stubborn.clearSession = () => undefined;
    const call = setup(stubborn);

    await Promise.allSettled([1, 2, 3].map(() => firstValueFrom(call('/api/things', throwError(() => error(401))))));

    expect(stubborn.login).toHaveBeenCalledTimes(1);
  });

  it.each(['/realms/processpuzzle-custom/protocol/openid-connect/token', 'https://auth.example/realms/x/account', '/auth/protocol/openid-connect/userinfo'])(
    'ignores a 401 from the identity provider (%s)',
    async (url) => {
      const call = setup();

      await expect(firstValueFrom(call(url, throwError(() => error(401, url))))).rejects.toBeDefined();

      expect(auth.login).not.toHaveBeenCalled();
      expect(auth.cleared).toBe(0);
    },
  );

  it('ignores a 401 from a third-party origin, and from a withheld root', async () => {
    const call = setup(auth, [], ['/third-party/']);

    await expect(firstValueFrom(call('https://analytics.example/collect', throwError(() => error(401))))).rejects.toBeDefined();
    await expect(firstValueFrom(call('/third-party/x', throwError(() => error(401))))).rejects.toBeDefined();

    expect(auth.login).not.toHaveBeenCalled();
  });

  it('reacts to a 401 from a configured API root', async () => {
    const call = setup(auth, ['http://localhost:8080']);

    await expect(firstValueFrom(call('http://localhost:8080/api/x', throwError(() => error(401))))).rejects.toBeDefined();

    expect(auth.login).toHaveBeenCalledTimes(1);
  });

  it.each([403, 404, 500, 0])('leaves a %s alone', async (status) => {
    const call = setup();

    await expect(firstValueFrom(call('/api/things', throwError(() => error(status))))).rejects.toBeDefined();

    expect(auth.login).not.toHaveBeenCalled();
    expect(reporter.showErrorMessage).not.toHaveBeenCalled();
  });

  it('passes successful responses through untouched', async () => {
    const call = setup();

    await expect(firstValueFrom(call('/api/things', of('ok')))).resolves.toBe('ok');

    expect(auth.login).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY)).toBeNull();
  });

  it('does not redirect an anonymous visitor, whose 401 only says the call needs a login', async () => {
    const anonymous = new StubAuthService(false);
    const call = setup(anonymous);

    await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();

    expect(anonymous.login).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(reporter.showErrorMessage).not.toHaveBeenCalled();
  });

  it('takes an anonymous visitor to the guidance page, not to the login, where the application says login is required', async () => {
    loginRequired = true;
    const anonymous = new StubAuthService(false);
    const call = setup(anonymous);

    await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();

    expect(anonymous.login).not.toHaveBeenCalled();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/session-expired'], { queryParams: { returnUrl: 'acme/orders?page=2' } });
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
  });

  it('does not navigate onto the guidance page it is already showing', async () => {
    loginRequired = true;
    router.url = '/auth/session-expired?returnUrl=acme';
    const call = setup(new StubAuthService(false));

    await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('mounts the guidance page at /auth/session-expired unless told otherwise', () => {
    TestBed.resetTestingModule();
    expect(TestBed.inject(SESSION_EXPIRED_ROUTE)).toBe('/auth/session-expired');
    expect(TestBed.inject(LOGIN_REQUIRED)()).toBe(false);
  });

  it('does nothing without an authentication service', async () => {
    const call = setup(null);

    await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();
  });

  describe('loop guard', () => {
    it('records the redirect in sessionStorage, which survives the document being replaced', async () => {
      const call = setup();

      await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();

      expect(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY)).toBe(String(clock));
    });

    it('does not redirect twice in the window, and reports the error instead', async () => {
      // The page after the first redirect: a fresh interceptor (the document was replaced), a signed-in user again.
      sessionStorage.setItem(SESSION_EXPIRED_REDIRECT_KEY, String(clock - 5_000));
      const call = setup();
      const original = error(401);

      await expect(firstValueFrom(call('/api/things', throwError(() => original)))).rejects.toBe(original);

      expect(auth.login).not.toHaveBeenCalled();
      expect(auth.cleared).toBe(0);
      expect(reporter.showErrorMessage).toHaveBeenCalledWith(SESSION_EXPIRED_MESSAGE, original);
      // And not a blank page: the user is taken to the page that explains and offers "Sign in again".
      expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/session-expired'], { queryParams: { returnUrl: 'acme/orders?page=2' } });
      expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
    });

    it('redirects again once the window has passed', async () => {
      sessionStorage.setItem(SESSION_EXPIRED_REDIRECT_KEY, String(clock - SESSION_EXPIRED_LOOP_WINDOW_MS - 1));
      const call = setup();

      await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();

      expect(auth.login).toHaveBeenCalledTimes(1);
      expect(reporter.showErrorMessage).not.toHaveBeenCalled();
    });

    it('forgets the record when the login could not start, so the next 401 may try again', async () => {
      auth.login.mockRejectedValueOnce(new Error('keycloak unreachable'));
      const call = setup();

      await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();
      await new Promise((resolve) => setTimeout(resolve));

      expect(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY)).toBeNull();
    });

    it('does not risk a redirect it could not record', async () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('quota');
      });
      const call = setup();

      await expect(firstValueFrom(call('/api/things', throwError(() => error(401))))).rejects.toBeDefined();
      setItem.mockRestore();

      expect(auth.login).not.toHaveBeenCalled();
      expect(reporter.showErrorMessage).toHaveBeenCalledWith(SESSION_EXPIRED_MESSAGE, expect.anything());
    });
  });
});
