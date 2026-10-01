import { Component, ComponentRef, computed, inject, OnDestroy, TemplateRef, viewChild, ViewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective, BaseUrlSegments } from '@processpuzzle/base-entity';
import { PUBLISH_BUTTON_I18N_KEY, PUBLISH_TOOLTIP_I18N_KEY } from '../base-app.i18n';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { createAppDefinitionDescriptor } from '../domain/app-definition.descriptors';
import { APP_PREVIEW_TAB } from './app-preview-tab';
import { AppConceptsGuideComponent } from './app-concepts-guide.component';

@Component({
  selector: 'pp-app-definition-container',
  standalone: true,
  imports: [AppConceptsGuideComponent, BaseEntityContainerComponent, MatButton, MatTooltip, TranslocoPipe],
  template: `
    <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>
    @if (isListPage()) {
      <pp-app-concepts-guide />
    }
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly entityDescriptor: BaseEntityDescriptor;
  readonly publishButtonKey = PUBLISH_BUTTON_I18N_KEY;
  readonly publishTooltipKey = PUBLISH_TOOLTIP_I18N_KEY;
  /** Only a definition the backend already knows can be promoted, so a not-yet-saved form cannot publish. */
  readonly canPublish = computed(() => !this.store.isLoading() && !!this.store.currentEntity()?.id);
  /**
   * The concepts guide belongs under the list only — on a details form it would push the definition's own
   * controls apart. Read off the matched child route rather than the URL, which carries a locale prefix.
   */
  readonly isListPage = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.childIsList()),
    ),
    { initialValue: this.childIsList() },
  );

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

  private childIsList(): boolean {
    return this.route.firstChild?.routeConfig?.path === BaseUrlSegments.ListForm;
  }

  async onPublish(): Promise<void> {
    const id = this.store.currentEntity()?.id;
    if (!id) return;
    await this.store.publish(id);
  }
}
