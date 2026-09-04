import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { createAppRoutes } from '../app.routes';
import { navigationItems } from './navigation-routes';

describe('navigationItems', () => {
  const itemsFor = (orgKey?: string) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter(createAppRoutes(orgKey))] });
    return navigationItems(TestBed.inject(Router));
  };

  it('reads the router it was given, so it follows the tenant the URL named', () => {
    expect(itemsFor('acme').map((item) => item.link)).toEqual(['/home', '/acme/admin']);
    expect(itemsFor().map((item) => item.link)).toEqual(['/home']);
  });

  it('makes every link absolute', () => {
    // A relative `acme/admin` resolves against the current route, so it works from `/home` and
    // produces `/acme/admin/organization-user/acme/admin` from inside the branch.
    for (const item of itemsFor('acme')) {
      expect(item.link.startsWith('/'), `'${item.link}' is relative`).toBeTruthy();
    }
  });

  it('carries the icon and label key the route declared', () => {
    expect(itemsFor('acme')).toEqual([
      { link: '/home', icon: 'home', menuTitle: 'home' },
      { link: '/acme/admin', icon: 'group', menuTitle: 'admin.users' },
    ]);
  });

  it('drops the routes that carry no title — the redirects and the auth matcher', () => {
    const routes = createAppRoutes('acme');
    expect(routes.length).toBeGreaterThan(itemsFor('acme').length);
  });
});
