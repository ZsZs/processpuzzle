import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { createAppDefinitionDescriptor } from '../domain/app-definition.descriptors';
import { APP_PREVIEW_TAB } from './app-preview-tab';

/**
 * The App Definition screens, with the Preview tab beside the form.
 *
 * There is no Publish button here any more. An application goes live together with everything it uses — its
 * modules, and the entities, rules and workflows its routes reach — so publishing is the organization's
 * release, made from the designer's Release section, not an action on one definition.
 */
@Component({
  selector: 'pp-app-definition-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: ` <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container> `,
})
export class AppDefinitionContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(AppDefinitionStore);
  readonly entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createAppDefinitionDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.extraTabs = [APP_PREVIEW_TAB];
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }
}
