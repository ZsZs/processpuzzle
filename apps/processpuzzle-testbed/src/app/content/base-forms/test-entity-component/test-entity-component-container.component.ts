import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { testEntityComponentDescriptors } from './test-entity-component.descriptors';
import { TestEntityComponentStore } from './test-entity-component.store';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'test-entity-component',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MarkdownComponent],
  templateUrl: 'test-entity-component-container.component.html',
  styles: ``,
})
export class TestEntityComponentContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(TestEntityComponentStore);
  baseEntityListOptions: BaseEntityDescriptor;

  constructor() {
    this.baseEntityListOptions = {
      entityName: 'Test Entity Component',
      store: this.store,
      attrDescriptors: testEntityComponentDescriptors,
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
