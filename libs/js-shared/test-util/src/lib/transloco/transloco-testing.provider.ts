import { provideTransloco, Translation, TRANSLOCO_LOADER, TranslocoLoader, TranslocoLoaderData, TranslocoService, TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import { firstValueFrom, Observable, of } from 'rxjs';
import { EnvironmentProviders, inject, Injectable, InjectionToken, makeEnvironmentProviders } from '@angular/core';
import { ComponentType } from '@angular/cdk/portal';
import { TestBed, TestModuleMetadata } from '@angular/core/testing';

export const mockLanguageConfig = {
  LANGUAGE_CONFIGURATION: {
    AVAILABLE_LANGUAGES: [
      { code: 'en', flag: 'flag-en', label: 'english' },
      { code: 'es', flag: 'flag-es', label: 'spanish' },
      { code: 'de', flag: 'flag-de', label: 'german' },
    ],
    DEFAULT_LANGUAGE: 'en',
  },
};

export type TranslationsMap = Record<string, Translation>;

export interface TranslocoTestConfig {
  availableLanguages?: { code: string; flag: string; label: string }[];
  defaultLanguage?: string;
  scope?: string;
  translations: TranslationsMap;
}

export const TRANSLOCO_TEST_CONFIG = new InjectionToken<TranslocoTestConfig>('TRANSLOCO_TEST_CONFIG');

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}
function pickNonObjects(obj: Translation): Translation {
  const out: Translation = {};
  for (const [key, value] of Object.entries(obj)) {
    if (!isPlainObject(value)) out[key] = value;
  }
  return out;
}

@Injectable({ providedIn: 'root' })
export class TestTranslocoLoader implements TranslocoLoader {
  private readonly config: TranslocoTestConfig = inject(TRANSLOCO_TEST_CONFIG);
  private readonly translations = this.config.translations;
  private readonly scope = this.config.scope;

  getTranslation(lang = 'en', _data?: TranslocoLoaderData): Observable<Translation> {
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
        availableLangs: mockLanguageConfig.LANGUAGE_CONFIGURATION.AVAILABLE_LANGUAGES.map((lang) => lang.code),
        defaultLang: mockLanguageConfig.LANGUAGE_CONFIGURATION.DEFAULT_LANGUAGE,
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
    imports?: TestModuleMetadata['imports'];
    providers?: TestModuleMetadata['providers'];
    defaultLang?: string;
  } = {},
) {
  await TestBed.configureTestingModule({
    imports: [
      componentType,
      TranslocoTestingModule.forRoot({
        langs: testConfig.translations,
        translocoConfig: { availableLangs: ['en', 'de'], defaultLang: opts.defaultLang ?? 'en' },
        preloadLangs: true,
      }),
      ...(opts.imports ?? []),
    ],
    providers: [...(opts.providers ?? [])],
  }).compileComponents();

  const translocoService = TestBed.inject(TranslocoService);
  const fixture = TestBed.createComponent(componentType);
  fixture.detectChanges();
  await fixture.whenStable();
  const component = fixture.componentInstance;

  return {
    component: component,
    fixture: fixture,
    translocoService,
    setActiveLang: async (lang: string) => {
      await firstValueFrom(translocoService.load(lang));
      translocoService.setActiveLang(lang);
      fixture.detectChanges();
      await fixture.whenStable();
    },
  };
}
