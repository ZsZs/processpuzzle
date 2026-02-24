import { provideTransloco, Translation, TRANSLOCO_LOADER, TranslocoLoader, TranslocoLoaderData, TranslocoService, TranslocoTestingOptions } from '@jsverse/transloco';
import { Observable, of } from 'rxjs';
import { EnvironmentProviders, inject, Injectable, InjectionToken, makeEnvironmentProviders } from '@angular/core';
import { ComponentType } from '@angular/cdk/portal';
import { TestBed } from '@angular/core/testing';

export const mockTranslocoService = {
  load: vi.fn(),
  setActiveLang: vi.fn(),
};

export const mockLanguageConfig = {
  AVAILABLE_LANGUAGES: [
    { code: 'en', flag: 'flag-en', label: 'english' },
    { code: 'es', flag: 'flag-es', label: 'spanish' },
    { code: 'de', flag: 'flag-de', label: 'german' },
  ],
  DEFAULT_LANGUAGE: 'en',
};

export type TranslationsMap = { [lang: string]: Record<string, any> | { [scope: string]: Record<string, any> } };

export interface TranslocoTestConfig {
  availableLanguages?: { code: string; flag: string; label: string }[];
  defaultLanguage?: string;
  scope?: string;
  translations: TranslationsMap;
}

export const TRANSLOCO_TEST_CONFIG = new InjectionToken<TranslocoTestConfig>('TRANSLOCO_TEST_CONFIG');

type AnyMap = Record<string, any>;
function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}
function pickNonObjects<T extends AnyMap>(obj: T): Partial<T> {
  const out: Partial<T> = {};
  for (const [k, v] of Object.entries(obj) as [keyof T, unknown][]) {
    if (!isPlainObject(v)) out[k] = v as T[keyof T];
  }
  return out;
}

@Injectable({ providedIn: 'root' })
export class TestTranslocoLoader implements TranslocoLoader {
  private readonly config: TranslocoTestConfig = inject(TRANSLOCO_TEST_CONFIG);
  private readonly translations = this.config.translations;
  private readonly scope = this.config.scope;

  getTranslation(lang = 'en', data?: TranslocoLoaderData): Observable<Translation> | Promise<Translation> {
    const langEntry = this.translations[lang];
    if (langEntry && typeof langEntry === 'object' && this.scope) {
      const scoped = (langEntry as Translation)[this.scope];
      return of(scoped ?? {});
    }
    if (langEntry && typeof langEntry === 'object' && !this.scope) {
      const hasScopes = Object.values(langEntry).some((v) => typeof v === 'object');
      if (hasScopes) {
        return of(pickNonObjects(langEntry));
      }
      return of(langEntry as Translation);
    }
    return of({});
  }
}

export function provideTranslocoTesting(testConfig: TranslocoTestConfig, options: TranslocoTestingOptions = {}): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: TRANSLOCO_LOADER, useFactory: () => new TestTranslocoLoader(), deps: [TRANSLOCO_TEST_CONFIG] },
    { provide: TRANSLOCO_TEST_CONFIG, useValue: testConfig },
    provideTransloco({
      config: {
        availableLangs: mockLanguageConfig.AVAILABLE_LANGUAGES.map((lang) => lang.code),
        defaultLang: mockLanguageConfig.DEFAULT_LANGUAGE,
        reRenderOnLangChange: false,
      },
      preloadLangs: true,
      ...options,
    }),
  ]);
}

export async function setUpTranslocoTestBed<T>(
  componentType: ComponentType<T>,
  testConfig: TranslocoTestConfig,
  opts: {
    imports?: any[];
    providers?: any[];
    defaultLang?: string;
  } = {},
) {
  await TestBed.configureTestingModule({
    imports: [componentType, ...(opts.imports ?? [])],
    providers: [provideTranslocoTesting(testConfig), ...(opts.providers ?? [])],
  }).compileComponents();

  const translocoService = TestBed.inject(TranslocoService);
  await translocoService.load('en').toPromise?.(); // ensure default language loaded

  const result = await TestBed.runInInjectionContext(async () => {
    const fixture = TestBed.createComponent(componentType);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    return { fixture, component };
  });

  return {
    component: result.component,
    fixture: result.fixture,
    translocoService,
    setActiveLang: async (lang: string) => {
      await translocoService.load(lang).toPromise?.();
      translocoService.setActiveLang(lang);
      result.fixture.detectChanges();
      await result.fixture.whenStable();
    },
  };
}
