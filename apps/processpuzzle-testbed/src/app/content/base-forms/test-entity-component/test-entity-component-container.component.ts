import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { TestEntityComponentStore } from './test-entity-component.store';
import { MarkdownComponent } from 'ngx-markdown';
import { createTestEntityComponentDescriptor } from './test-entity-component.descriptors';
import { TestEntityComponent } from './test-entity-component';

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
  entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createTestEntityComponentDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.entityTitle = () => (this.store.currentEntity() as TestEntityComponent).name ?? '';
    this.entityDescriptor.overwriteLinkedEntityAttr('testEntityId', 'Test Entity');
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
