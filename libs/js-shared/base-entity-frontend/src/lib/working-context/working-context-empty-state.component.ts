import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { translateLabel } from '../i18n/entity-label.pipe';
import { WorkingContextPickerComponent } from './working-context-picker.component';
import { WorkingContextService } from './working-context.service';
import { WORKING_CONTEXT_ROUTE_DATA_KEY } from './working-context.url';

/**
 * What a view that requires a context shows without one: "Select a …", with the picker right there. Picking
 * stays on the same view — the URL gains the context's id — so the user never takes a detour through a list.
 */
@Component({
  selector: 'pp-working-context-empty-state',
  standalone: true,
  imports: [TranslocoPipe, WorkingContextPickerComponent],
  template: `
    <section class="empty-state" data-testid="working-context-empty-state">
      <h2>{{ 'base_entity.working_context.empty_state' | transloco: { entity: entityLabel() } }}</h2>
      <pp-working-context-picker [contextName]="contextName" />
    </section>
  `,
  styles: [
    `
      .empty-state {
        max-width: 480px;
        margin: 24px auto;
        padding: 16px;
      }
    `,
  ],
})
export class WorkingContextEmptyStateComponent {
  protected readonly contextName = inject(ActivatedRoute).snapshot.data[WORKING_CONTEXT_ROUTE_DATA_KEY] as string;
  private readonly service = inject(WorkingContextService);
  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly transloco = inject(TranslocoService);

  protected readonly entityLabel = computed(() => {
    const entityName = this.service.definitionOf(this.contextName)?.entityName ?? this.contextName;
    const descriptor = this.descriptorRegistry.getDescriptor(entityName);
    return descriptor ? translateLabel(this.transloco, descriptor.i18nKey(), entityName) : entityName;
  });
}
