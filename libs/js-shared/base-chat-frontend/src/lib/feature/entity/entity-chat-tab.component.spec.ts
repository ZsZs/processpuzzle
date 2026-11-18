import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { EntityDefinitionRegistry, type BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { chatTranslations } from '../../test-translations';
import { ChatPanelComponent } from '../panel/chat-panel.component';
import { EntityChatTabComponent, entityCodeOf } from './entity-chat-tab.component';

/** Stands in for the real panel, which needs the chat store and the identity; the tab only has to hand it its inputs. */
@Component({ selector: 'pp-chat-panel', standalone: true, template: '' })
class StubChatPanelComponent {
  readonly subjectId = input<string | undefined>(undefined);
  readonly definitionKey = input<string | undefined>(undefined);
}

describe('EntityChatTabComponent', () => {
  const forEntity = vi.fn();
  const byName = vi.fn();

  async function render(entityName: string | undefined, entityId = 'o-1'): Promise<ComponentFixture<EntityChatTabComponent>> {
    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({ translations: chatTranslations() }),
        { provide: ChatDefinitionLookup, useValue: { forEntity } },
        { provide: EntityDefinitionRegistry, useValue: { byName } },
        { provide: ROUTER_OUTLET_DATA, useValue: signal(entityName ? ({ entityName } as BaseEntityDescriptor) : undefined) },
      ],
    });
    TestBed.overrideComponent(EntityChatTabComponent, { remove: { imports: [ChatPanelComponent] }, add: { imports: [StubChatPanelComponent] } });
    const fixture = TestBed.createComponent(EntityChatTabComponent);
    fixture.componentRef.setInput('entityId', entityId);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    return fixture;
  }

  const sections = (fixture: ComponentFixture<unknown>) => [...(fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="entity-chat"]')];

  beforeEach(() => {
    TestBed.resetTestingModule();
    forEntity.mockReset();
    byName.mockReset();
    byName.mockResolvedValue({ code: 'dynamic-entity' });
  });

  it('shows the one chat of the entity as a panel on the selected object, without a heading', async () => {
    forEntity.mockResolvedValue([{ id: 'dynamic-entity-topics', name: 'Dynamic Entity topics' }]);

    const fixture = await render('Dynamic Entity', 'd-7');

    expect(forEntity).toHaveBeenCalledWith('dynamic-entity');
    expect(sections(fixture)).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('h3')).toBeNull();
    const panel = fixture.debugElement.query((element) => element.name === 'pp-chat-panel').componentInstance as StubChatPanelComponent;
    expect([panel.subjectId(), panel.definitionKey()]).toEqual(['d-7', 'dynamic-entity-topics']);
  });

  it('stacks several chats under their names, each with its own definition', async () => {
    forEntity.mockResolvedValue([
      { id: 'order-discussion', name: 'Order discussion' },
      { id: 'order-topics', name: 'Order topics' },
    ]);

    const fixture = await render('Order');

    expect(sections(fixture)).toHaveLength(2);
    expect([...(fixture.nativeElement as HTMLElement).querySelectorAll('h3')].map((heading) => heading.textContent)).toEqual(['Order discussion', 'Order topics']);
    const panels = fixture.debugElement.queryAll((element) => element.name === 'pp-chat-panel').map((element) => element.componentInstance as StubChatPanelComponent);
    expect(panels.map((panel) => [panel.subjectId(), panel.definitionKey()])).toEqual([
      ['o-1', 'order-discussion'],
      ['o-1', 'order-topics'],
    ]);
  });

  it('shows nothing when the route offers no entity', async () => {
    forEntity.mockResolvedValue([]);

    const fixture = await render(undefined);

    expect(forEntity).toHaveBeenCalledWith(undefined);
    expect(sections(fixture)).toHaveLength(0);
  });
});

describe('entityCodeOf', () => {
  it('takes the code of the definition the name belongs to, else the snake-cased name, and nothing for no name', async () => {
    const registry = { byName: vi.fn().mockResolvedValueOnce({ code: 'dynamic-entity' }).mockRejectedValueOnce(new Error('unknown')) } as unknown as EntityDefinitionRegistry;

    expect(await entityCodeOf(registry, 'Dynamic Entity')).toBe('dynamic-entity');
    expect(await entityCodeOf(registry, 'Special Order')).toBe('special-order');
    expect(await entityCodeOf(registry, undefined)).toBeUndefined();
  });
});
