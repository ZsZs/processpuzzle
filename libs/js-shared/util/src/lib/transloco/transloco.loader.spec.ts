import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Translation } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RUNTIME_CONFIGURATION } from '../runtime-configuration/configuration.injection-tokens';
import { TranslocoHttpLoader } from './transloco.loader';
import { TRANSLATION_SOURCE_REGISTRY, TranslationSource } from './translation-source';

const APP_ROOT = 'http://localhost:8080/organizations/processpuzzle-testbed';
const WIDGET_ROOT = 'http://localhost:8081/organizations/processpuzzle-testbed';

describe('TranslocoHttpLoader', () => {
  let loader: TranslocoHttpLoader;
  let http: HttpTestingController;

  function configure(options: { runtimeConfiguration?: object | null; sources?: TranslationSource[] } = {}) {
    const { runtimeConfiguration = { BASE_CONFIGURATION: { APP_SERVICE_ROOT: APP_ROOT, WIDGET_SERVICE_ROOT: WIDGET_ROOT } }, sources } = options;

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TranslocoHttpLoader,
        ...(runtimeConfiguration ? [{ provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration }] : []),
        ...(sources ?? []).map((source) => ({ provide: TRANSLATION_SOURCE_REGISTRY, useValue: source, multi: true })),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    loader = TestBed.inject(TranslocoHttpLoader);
  }

  /** Subscribes and collects the one bundle the loader emits. */
  function load(path: string, scope?: string): { readonly value: Translation | undefined } {
    const result: { value?: Translation } = {};
    loader.getTranslation(path, scope ? { scope } : undefined).subscribe((bundle) => (result.value = bundle));
    return result as { readonly value: Translation | undefined };
  }

  beforeEach(() => configure());

  afterEach(() => http.verify());

  it('reads the root bundle from the assets', () => {
    const result = load('en');

    http.expectOne('assets/i18n/en.json').flush({ home: 'Home' });

    expect(result.value).toEqual({ home: 'Home' });
  });

  it('reads a scoped bundle from the assets, at the path transloco composed', () => {
    const result = load('base_app/en', 'base_app');

    http.expectOne('assets/i18n/base_app/en.json').flush({ publish: { button: 'Publish' } });

    expect(result.value).toEqual({ publish: { button: 'Publish' } });
  });

  // The case the whole fallback exists for: a designer-authored module's scope is named at run-time, so
  // no build could have shipped an asset for it.
  it('falls back to the owning backend when the asset is missing', () => {
    const result = load('order_admin/en', 'order_admin');

    http.expectOne('assets/i18n/order_admin/en.json').flush('', { status: 404, statusText: 'Not Found' });
    http.expectOne(`${APP_ROOT}/app/translations/order_admin/en`).flush({ module: { name: 'Order administration' } });

    expect(result.value).toEqual({ module: { name: 'Order administration' } });
  });

  it('asks the backend for the root bundle without a scope segment', () => {
    const result = load('en');

    http.expectOne('assets/i18n/en.json').flush('', { status: 404, statusText: 'Not Found' });
    http.expectOne(`${APP_ROOT}/app/translations/en`).flush({ home: 'Home' });

    expect(result.value).toEqual({ home: 'Home' });
  });

  // nginx and Firebase Hosting rewrite an unknown path to index.html and answer 200. HttpClient turns the
  // unparseable body into an error by itself, so the fallback still fires.
  it('treats an index.html rewrite as a miss rather than a bundle', () => {
    const result = load('order_admin/en', 'order_admin');

    http.expectOne('assets/i18n/order_admin/en.json').flush('<!doctype html><html lang="en"></html>');
    http.expectOne(`${APP_ROOT}/app/translations/order_admin/en`).flush({ module: { name: 'From the backend' } });

    expect(result.value).toEqual({ module: { name: 'From the backend' } });
  });

  // The gap a bare catchError leaves: an empty 200 body parses to null and raises no error at all.
  it('treats an empty 200 body as a miss rather than a bundle', () => {
    const result = load('order_admin/en', 'order_admin');

    http.expectOne('assets/i18n/order_admin/en.json').flush(null, { status: 200, statusText: 'OK' });
    http.expectOne(`${APP_ROOT}/app/translations/order_admin/en`).flush({ module: { name: 'From the backend' } });

    expect(result.value).toEqual({ module: { name: 'From the backend' } });
  });

  // Resolving empty is load-bearing: an error here would make transloco throw TranslationLoadError for
  // the fallback language and take the component tree down.
  it('resolves to an empty bundle when the backend fails too', () => {
    const result = load('order_admin/en', 'order_admin');

    http.expectOne('assets/i18n/order_admin/en.json').flush('', { status: 404, statusText: 'Not Found' });
    http.expectOne(`${APP_ROOT}/app/translations/order_admin/en`).flush('', { status: 500, statusText: 'Server Error' });

    expect(result.value).toEqual({});
  });

  it('resolves to an empty bundle when no runtime configuration names a backend', () => {
    configure({ runtimeConfiguration: null });

    const result = load('order_admin/en', 'order_admin');
    http.expectOne('assets/i18n/order_admin/en.json').flush('', { status: 404, statusText: 'Not Found' });

    expect(result.value).toEqual({});
  });

  it('resolves to an empty bundle when the configuration carries no BASE_CONFIGURATION', () => {
    configure({ runtimeConfiguration: {} });

    const result = load('order_admin/en', 'order_admin');
    http.expectOne('assets/i18n/order_admin/en.json').flush('', { status: 404, statusText: 'Not Found' });

    expect(result.value).toEqual({});
  });

  describe('scope routing', () => {
    const widgetSource: TranslationSource = { scopes: ['base_widget', 'widgets'], serviceRootKey: 'WIDGET_SERVICE_ROOT', segment: 'widget' };

    it('asks the feature that owns the scope, at that feature`s own root and segment', () => {
      configure({ sources: [widgetSource] });

      load('widgets/en', 'widgets');
      http.expectOne('assets/i18n/widgets/en.json').flush('', { status: 404, statusText: 'Not Found' });

      http.expectOne(`${WIDGET_ROOT}/widget/translations/widgets/en`).flush({});
    });

    // Unregistered scopes are the designer-authored ones, and modules are base-app's aggregate.
    it('falls back to base-app for a scope no feature claims', () => {
      configure({ sources: [widgetSource] });

      load('order_admin/en', 'order_admin');
      http.expectOne('assets/i18n/order_admin/en.json').flush('', { status: 404, statusText: 'Not Found' });

      http.expectOne(`${APP_ROOT}/app/translations/order_admin/en`).flush({});
    });

    // The per-feature roots are optional; one host serves every feature today.
    it('uses APP_SERVICE_ROOT when the feature declares no root of its own', () => {
      configure({
        runtimeConfiguration: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: APP_ROOT } },
        sources: [{ scopes: ['base_state'], serviceRootKey: 'STATE_SERVICE_ROOT', segment: 'state' }],
      });

      load('base_state/en', 'base_state');
      http.expectOne('assets/i18n/base_state/en.json').flush('', { status: 404, statusText: 'Not Found' });

      http.expectOne(`${APP_ROOT}/state/translations/base_state/en`).flush({});
    });
  });
});
