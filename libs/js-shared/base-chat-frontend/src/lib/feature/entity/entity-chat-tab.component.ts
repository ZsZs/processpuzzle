import { ChangeDetectionStrategy, Component, effect, inject, input, signal, Signal, untracked } from '@angular/core';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { type BaseEntityDescriptor, EntityDefinitionRegistry, snakeCaseName } from '@processpuzzle/base-entity';
import { BASE_CHAT_TRANSLOCO_SCOPE } from '../../base-chat.i18n';
import { ChatDefinition } from '../../domain/chat-definition';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { ChatPanelComponent } from '../panel/chat-panel.component';

/**
 * An entity's Chat tab, mounted at `<entity>/<id>/chat`: the chats defined for this kind of object, each as a
 * `chat-panel` on this object. One chat is shown without a heading; several are stacked under their names.
 */
@Component({
  selector: 'pp-entity-chat-tab',
  standalone: true,
  imports: [ChatPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideTranslocoScope({ scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE })],
  template: `
    <div class="pp-entity-chat">
      @for (chat of chats(); track chat.id) {
        <section class="pp-entity-chat__chat" data-testid="entity-chat">
          @if (chats().length > 1) {
            <h3 class="pp-entity-chat__name">{{ chat.name }}</h3>
          }
          <pp-chat-panel [subjectId]="entityId()" [definitionKey]="chat.id" />
        </section>
      }
    </div>
  `,
  styles: [
    `
      .pp-entity-chat {
        display: flex;
        flex-direction: column;
        gap: 1.5rem;
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-entity-chat__name {
        margin: 0 0 0.5rem;
        font-size: 1rem;
      }
    `,
  ],
})
export class EntityChatTabComponent {
  /** Bound from the route's `:entityId` param — the object the chat is about. */
  readonly entityId = input.required<string>();

  private readonly outletData = inject(ROUTER_OUTLET_DATA, { optional: true }) as Signal<BaseEntityDescriptor | undefined> | null;
  private readonly definitions = inject(ChatDefinitionLookup);
  private readonly entities = inject(EntityDefinitionRegistry);

  protected readonly chats = signal<ChatDefinition[]>([]);

  constructor() {
    effect(() => {
      const entityName = this.outletData?.()?.entityName;
      untracked(() => void this.load(entityName));
    });
  }

  private async load(entityName: string | undefined): Promise<void> {
    this.chats.set(await this.definitions.forEntity(await entityCodeOf(this.entities, entityName)));
  }
}

/**
 * The entity definition code a descriptor's name stands for — the key a chat definition's `subjectDefinition` holds.
 * The snake-case of the name stands in for an entity that has no definition row, as it does for the URL segment.
 */
export async function entityCodeOf(registry: EntityDefinitionRegistry, entityName: string | undefined): Promise<string | undefined> {
  if (!entityName) return undefined;
  const definition = await registry.byName(entityName).catch(() => undefined);
  return definition?.code ?? snakeCaseName(entityName);
}
