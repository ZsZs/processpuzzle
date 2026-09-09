import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { NavItem } from '../domain/app-definition';
import { createNavItemDescriptor } from '../domain/nav-item.descriptors';

/**
 * One facade covers every level of the nav tree, however deep: the store is a root singleton and the row
 * it stands for comes from the route, so `app-nav-item/a/details/app-nav-item/b/details` resolves through
 * the same facade as the top level.
 */
@Injectable()
export class AppNavItemFacade extends EmbeddedEntityFacade<NavItem> {
  readonly entityType = NavItem;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createNavItemDescriptor();
  }
}
