import { describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
import { createChatIdentity, createDocumentAccessContext } from './app.config';

describe('createDocumentAccessContext', () => {
  const contextFor = (currentUser: ReturnType<typeof signal<{ roles?: string[] } | undefined>>) => {
    TestBed.configureTestingModule({ providers: [{ provide: AUTHENTICATION_SERVICE, useValue: { currentUser } }] });
    return TestBed.runInInjectionContext(createDocumentAccessContext);
  };

  it('hands the signed-in user roles to the documents', () => {
    const context = contextFor(signal({ roles: ['documentation-editor'] }));

    expect(context.roles()).toEqual(['documentation-editor']);
    expect(context.authenticated?.()).toBe(true);
  });

  it('has no roles while nobody is signed in', () => {
    const context = contextFor(signal(undefined));

    expect(context.roles()).toEqual([]);
    expect(context.authenticated?.()).toBe(false);
  });
});

describe('createChatIdentity', () => {
  const identityFor = (auth: object) => {
    TestBed.configureTestingModule({ providers: [{ provide: AUTHENTICATION_SERVICE, useValue: auth }] });
    return TestBed.runInInjectionContext(createChatIdentity);
  };

  it('hands chat the token, refreshed first when it is about to expire', async () => {
    const calls: string[] = [];
    const identity = identityFor({
      refreshAccessToken: async (minValiditySeconds: number) => {
        calls.push(`refresh:${minValiditySeconds}`);
        return true;
      },
      getAccessToken: () => {
        calls.push('token');
        return 'tok-1';
      },
      currentUser: signal(undefined),
    });

    expect(await identity.accessToken()).toBe('tok-1');
    expect(calls).toEqual(['refresh:60', 'token']);
  });

  it('hands over the token it has when it cannot refresh it', async () => {
    const identity = identityFor({
      refreshAccessToken: async () => {
        throw new Error('offline');
      },
      getAccessToken: () => 'old-token',
      currentUser: signal(undefined),
    });

    expect(await identity.accessToken()).toBe('old-token');
  });

  it('has no token for a provider with none', async () => {
    const identity = identityFor({ refreshAccessToken: async () => false, getAccessToken: () => undefined, currentUser: signal(undefined) });

    expect(await identity.accessToken()).toBeUndefined();
  });

  it('knows the signed-in user by the id messages carry as their author, and nobody otherwise', () => {
    const user = signal<{ id: string } | undefined>({ id: 'u-7' });
    const identity = identityFor({ refreshAccessToken: async () => false, getAccessToken: () => undefined, currentUser: user });
    expect(identity.userId()).toBe('u-7');

    user.set(undefined);
    expect(identity.userId()).toBeUndefined();
  });
});
