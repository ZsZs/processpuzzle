import { Component, computed, inject, TemplateRef, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { PUBLISH_BUTTON_I18N_KEY, PUBLISH_TOOLTIP_I18N_KEY } from '../base-widget.i18n';
import { createWidgetDefinitionDescriptor } from './widget-definition.descriptors';
import { WidgetDefinitionStore } from './widget-definition.store';

/**
 * Hosts the generic container for `Widget Definition`: binds the descriptor to the store the routable
 * screens read from, and contributes the `Publish` form action — a widget type has a lifecycle of its own,
 * because its props schema is a contract with every instance that names it.
 */
@Component({
  selector: 'pp-widget-definition-container',
  standalone: true,
  imports: [BaseEntityContainerComponent, MatButton, MatTooltip, TranslocoPipe],
  template: `
    <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>
    <ng-template #publishActionsTpl>
      <button id="publish" type="button" mat-raised-button color="accent" [disabled]="!canPublish()" [matTooltip]="publishTooltipKey | transloco" (click)="onPublish()">{{ publishButtonKey | transloco }}</button>
    </ng-template>
  `,
})
export class WidgetDefinitionContainerComponent {
  readonly publishActionsTpl = viewChild<TemplateRef<unknown>>('publishActionsTpl');
  readonly entityDescriptor: BaseEntityDescriptor;
  readonly publishButtonKey = PUBLISH_BUTTON_I18N_KEY;
  readonly publishTooltipKey = PUBLISH_TOOLTIP_I18N_KEY;
  private readonly store = inject(WidgetDefinitionStore);
  /** Only a definition the backend already knows can be promoted, so a not-yet-saved form cannot publish. */
  readonly canPublish = computed(() => !this.store.isLoading() && !!this.store.currentEntity()?.id);

  constructor() {
    this.entityDescriptor = createWidgetDefinitionDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.extraFormActionsTemplate = () => this.publishActionsTpl();
  }

  async onPublish(): Promise<void> {
    const id = this.store.currentEntity()?.id;
    if (!id) return;
    await this.store.publish(id);
  }
}
