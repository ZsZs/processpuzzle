import { describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
import { createDocumentAccessContext } from './app.config';

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
