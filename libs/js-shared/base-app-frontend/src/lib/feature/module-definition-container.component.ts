import { Component, inject } from '@angular/core';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { createModuleDefinitionDescriptor } from '../domain/module-definition.descriptors';
import { ModuleDefinitionStore } from '../domain/module-definition.store';

/**
 * Hosts the generic container for `Module Definition`, whose only job is to bind the descriptor to the
 * store the routable screens read from.
 *
 * Deliberately without the `Publish` action `AppDefinitionContainerComponent` contributes: publishing is
 * an application operation. An app is what a user navigates to and its status is what decides whether a
 * mount is live, so a module with a lifecycle of its own would make "which version of what is live" a
 * question with two answers.
 */
@Component({
  selector: 'pp-module-definition-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>`,
})
export class ModuleDefinitionContainerComponent {
  private readonly store = inject(ModuleDefinitionStore);
  readonly entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createModuleDefinitionDescriptor();
    this.entityDescriptor.store = this.store;
  }
}
