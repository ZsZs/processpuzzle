import { describe, expect, it } from 'vitest';
import { BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { BASE_EVENT_ROUTES } from './base-event.routes';

describe('BASE_EVENT_ROUTES', () => {
  const [definitionRoute] = BASE_EVENT_ROUTES;

  it('registers the event definition as its only routable aggregate, at the snake-cased entity name', () => {
    expect(BASE_EVENT_ROUTES.map((route) => route.path)).toEqual(['event-definition']);
  });

  it('advertises itself to the sidenav and to the entity route registry', () => {
    expect(definitionRoute.title).toBeTruthy();
    expect(definitionRoute.data).toEqual({ icon: 'bolt', menuTitle: 'event.definitions', entityName: 'Event Definition' });
  });

  it('binds the generic container to this feature facade and registers both transloco scopes with their aliases', () => {
    const providers = (definitionRoute.providers?.flat() ?? []) as Array<{ useValue?: unknown; provide?: unknown }>;

    expect(definitionRoute.component).toBe(BaseEntityContainerComponent);
    expect(providers[0].provide).toBeDefined();
    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_event', alias: 'base_event' },
    ]);
  });

  it('nests the generic list and details routes', () => {
    expect(definitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });
});
