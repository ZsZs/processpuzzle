import { Component, ComponentRef, computed, inject, OnDestroy, TemplateRef, viewChild, ViewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { PUBLISH_BUTTON_I18N_KEY, PUBLISH_TOOLTIP_I18N_KEY } from '../base-app.i18n';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { createAppDefinitionDescriptor } from '../domain/app-definition.descriptors';
import { APP_PREVIEW_TAB } from './app-preview-tab';

@Component({
  selector: 'pp-app-definition-container',
  standalone: true,
  imports: [BaseEntityContainerComponent, MatButton, MatTooltip, TranslocoPipe],
  template: `
    <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>
    <ng-template #publishActionsTpl>
      <button id="publish" type="button" mat-raised-button color="accent" [disabled]="!canPublish()" [matTooltip]="publishTooltipKey | transloco" (click)="onPublish()">
        {{ publishButtonKey | transloco }}
      </button>
    </ng-template>
  `,
})
export class AppDefinitionContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  readonly publishActionsTpl = viewChild<TemplateRef<unknown>>('publishActionsTpl');
  private readonly store = inject(AppDefinitionStore);
  readonly entityDescriptor: BaseEntityDescriptor;
  readonly publishButtonKey = PUBLISH_BUTTON_I18N_KEY;
  readonly publishTooltipKey = PUBLISH_TOOLTIP_I18N_KEY;
  /** Only a definition the backend already knows can be promoted, so a not-yet-saved form cannot publish. */
  readonly canPublish = computed(() => !this.store.isLoading() && !!this.store.currentEntity()?.id);

  constructor() {
    this.entityDescriptor = createAppDefinitionDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.extraFormActionsTemplate = () => this.publishActionsTpl();
    this.entityDescriptor.extraTabs = [APP_PREVIEW_TAB];
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }

  async onPublish(): Promise<void> {
    const id = this.store.currentEntity()?.id;
    if (!id) return;
    await this.store.publish(id);
  }
}
