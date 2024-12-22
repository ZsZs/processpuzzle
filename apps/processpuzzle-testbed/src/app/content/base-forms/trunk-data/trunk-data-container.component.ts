import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { trunkDataDescriptors } from './trunk-data.descriptors';
import { TrunkDataStore } from './trunk-data.store';

@Component({
  selector: 'trunk-data',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent],
  template: ` <base-entity-container [baseEntityListOptions]="baseEntityListOptions"></base-entity-container> `,
  styles: ``,
})
export class TrunkDataContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(TrunkDataStore);
  baseEntityListOptions: BaseEntityDescriptor;

  constructor() {
    this.baseEntityListOptions = {
      entityName: 'Trunk Data',
      store: this.store,
      columnDescriptors: trunkDataDescriptors,
      entityTitle: "this.store.currentEntity() ? this.store.currentEntity().name : ''",
    };
  }

  // region Angular lifecycle hooks
  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }

  // endregion

  // protected, private helper methods
  // endregion
}
