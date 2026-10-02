import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { injectActiveLang } from './active-lang';

describe('injectActiveLang', () => {
  it('starts with the active language and follows langChanges$', () => {
    const langChanges$ = new BehaviorSubject('en');
    TestBed.configureTestingModule({ providers: [{ provide: TranslocoService, useValue: { langChanges$, getActiveLang: () => 'en' } }] });

    const lang = runInInjectionContext(TestBed.inject(Injector), () => injectActiveLang());
    expect(lang()).toBe('en');

    langChanges$.next('hu');
    expect(lang()).toBe('hu');
  });
});
