import { Component, inject, Signal } from '@angular/core';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityListComponent } from '../base-list/base-entity-list.component';
import { BaseEntityToolbarComponent } from '../base-toolbar/base-entity-toolbar.component';

/**
 * The list of one kind of embedded part, for an aggregate that is the context object: the ordinary toolbar and the
 * ordinary list, over the rows of the aggregate's payload. Nothing here is specific to the parts — an embedded entity
 * has no screens of its own except its form, and this is all a list of it needs on top.
 */
@Component({
  selector: 'base-embedded-list-screen',
  standalone: true,
  imports: [BaseEntityToolbarComponent, BaseEntityListComponent],
  template: `
    <base-entity-toolbar [entityDescriptor]="entityDescriptor()" />
    <base-list />
  `,
})
export class EmbeddedListScreenComponent {
  protected readonly entityDescriptor = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
}
