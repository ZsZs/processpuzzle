import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { OrderLineStore } from './order-line.store';
import { createOrderLineDescriptor } from './order-line.descriptors';
import { OrderLine } from './order-line';

@Component({
  selector: 'order-line-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>`,
  styles: ``,
})
export class OrderLineContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(OrderLineStore);
  entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createOrderLineDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.entityTitle = () => (this.store.currentEntity() as OrderLine)?.productName ?? '';
    this.entityDescriptor.overwriteLinkedEntityAttr('orderId', 'Order');
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }
}
