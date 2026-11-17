import { TestBed } from '@angular/core/testing';
import { ENTITY_TAB_CONTRIBUTORS, EntityDefinitionRegistry, type BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { ENTITY_CHAT_TAB, EntityChatTabContributor, provideEntityChatTab } from './entity-chat-tab.contributor';

describe('EntityChatTabContributor', () => {
  const forEntity = vi.fn();
  const byName = vi.fn();

  function contributor(): EntityChatTabContributor {
    TestBed.configureTestingModule({
      providers: [
        { provide: ChatDefinitionLookup, useValue: { forEntity } },
        { provide: EntityDefinitionRegistry, useValue: { byName } },
      ],
    });
    return TestBed.inject(EntityChatTabContributor);
  }

  const descriptor = (entityName: string) => ({ entityName }) as BaseEntityDescriptor;

  beforeEach(() => {
    TestBed.resetTestingModule();
    forEntity.mockReset();
    byName.mockReset();
  });

  it('offers the Chat tab to an entity some chat is defined for, looking the chats up by definition code', async () => {
    byName.mockResolvedValue({ code: 'dynamic-entity' });
    forEntity.mockResolvedValue([{ id: 'dynamic-entity-topics' }]);

    expect(await contributor().tabsFor(descriptor('Dynamic Entity'))).toEqual([ENTITY_CHAT_TAB]);
    expect(forEntity).toHaveBeenCalledWith('dynamic-entity');
  });

  it('offers nothing to an entity without a chat definition', async () => {
    byName.mockResolvedValue({ code: 'boat' });
    forEntity.mockResolvedValue([]);

    expect(await contributor().tabsFor(descriptor('Boat'))).toEqual([]);
  });

  it('falls back to the snake-cased name for an entity without a definition row', async () => {
    byName.mockRejectedValue(new Error('unknown'));
    forEntity.mockResolvedValue([]);

    await contributor().tabsFor(descriptor('Special Order'));

    expect(forEntity).toHaveBeenCalledWith('special-order');
  });

  it('registers itself as a tab contributor', () => {
    const registration = provideEntityChatTab().find((provider) => (provider as { provide?: unknown }).provide === ENTITY_TAB_CONTRIBUTORS);

    expect(registration).toMatchObject({ useExisting: EntityChatTabContributor, multi: true });
  });
});
