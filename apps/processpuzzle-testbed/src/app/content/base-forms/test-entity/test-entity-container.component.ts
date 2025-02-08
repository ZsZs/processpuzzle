import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { TestEntityStore } from './test-entity.store';
import { testEntityDescriptors } from './test-entity.descriptors';

@Component({
  selector: 'test-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent],
  template: ` <base-entity-container [baseEntityListOptions]="baseEntityListOptions"></base-entity-container> `,
  styles: ``,
})
export class TestEntityContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(TestEntityStore);
  baseEntityListOptions: BaseEntityDescriptor;

  constructor() {
    this.baseEntityListOptions = {
      entityName: 'Test Entity',
      store: this.store,
      attrDescriptors: testEntityDescriptors,
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
