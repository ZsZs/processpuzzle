import { BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { BASE_CHAT_ROUTES } from './base-chat.routes';

describe('BASE_CHAT_ROUTES', () => {
  const [definitionRoute] = BASE_CHAT_ROUTES;

  it('registers the chat definition as its only routable aggregate, at the snake-cased entity name', () => {
    expect(BASE_CHAT_ROUTES.map((route) => route.path)).toEqual(['chat-definition']);
    expect(definitionRoute.data).toMatchObject({ entityName: 'Chat Definition' });
    expect(definitionRoute.title).toBeTruthy();
  });

  it('binds the generic container and registers both transloco scopes with their aliases', () => {
    const providers = (definitionRoute.providers?.flat() ?? []) as Array<{ useValue?: unknown; provide?: unknown }>;

    expect(definitionRoute.component).toBe(BaseEntityContainerComponent);
    expect(providers[0].provide).toBeDefined();
    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_chat', alias: 'base_chat' },
    ]);
  });

  it('nests the generic list and details routes', () => {
    expect(definitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });
});
