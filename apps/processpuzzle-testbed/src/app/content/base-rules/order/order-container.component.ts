import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { OrderStore } from './order.store';
import { createOrderDescriptor } from './order.descriptors';
import { Order } from './order';

@Component({
  selector: 'order-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>`,
  styles: ``,
})
export class OrderContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(OrderStore);
  entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createOrderDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.entityTitle = () => (this.store.currentEntity() as Order)?.orderNumber ?? '';
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }
}
