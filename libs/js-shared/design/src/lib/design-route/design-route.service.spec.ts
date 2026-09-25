import { describe, expect, it } from 'vitest';
import { Component, Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DefaultUrlSerializer, provideRouter, Router, UrlSerializer, UrlTree } from '@angular/router';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { DesignRouteService } from './design-route.service';

@Component({ template: '' })
class BlankComponent {}

/** Decorates every URL the way `LocaleUrlSerializer` does, without depending on transloco. */
@Injectable()
class PrefixingUrlSerializer extends DefaultUrlSerializer {
  override parse(url: string): UrlTree {
    return super.parse(url.replace(/^\/en(?=\/|$)/, '') || '/');
  }

  override serialize(tree: UrlTree): string {
    return `/en${super.serialize(tree)}`;
  }
}

const routes = [
  { path: 'home', component: BlankComponent },
  { path: 'design', component: BlankComponent },
  { path: 'acme/design', component: BlankComponent },
];

function setup(providers: unknown[] = []) {
  TestBed.configureTestingModule({ providers: [provideRouter(routes), ...(providers as never[])] });
  return { router: TestBed.inject(Router), service: TestBed.inject(DesignRouteService) };
}

describe('DesignRouteService', () => {
  it('recognises the design route', async () => {
    const { router, service } = setup();
    await router.navigateByUrl('/design');
    expect(service.isDesignRoute()).toBe(true);

    await router.navigateByUrl('/home');
    expect(service.isDesignRoute()).toBe(false);
  });

  it('recognises the design route when the serializer prefixes the language', async () => {
    const { router, service } = setup([{ provide: UrlSerializer, useClass: PrefixingUrlSerializer }]);
    await router.navigateByUrl('/en/design');

    expect(router.url).toBe('/en/design');
    expect(service.isDesignRoute()).toBe(true);
  });

  it('honours the design route prefix under a language prefix', async () => {
    const { router, service } = setup([
      { provide: UrlSerializer, useClass: PrefixingUrlSerializer },
      { provide: DESIGN_ROUTE_PREFIX, useValue: '/acme' },
    ]);
    await router.navigateByUrl('/en/acme/design');

    expect(service.isDesignRoute()).toBe(true);
  });
});
