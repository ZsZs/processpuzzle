import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { TestEntityStore } from './test-entity.store';
import { createTestEntityDescriptor } from './test-entity.descriptors';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'test-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MarkdownComponent],
  templateUrl: './test-entity-container.component.html',
  styles: ``,
})
export class TestEntityContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(TestEntityStore);
  baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.baseEntityDescriptor = createTestEntityDescriptor();
    this.baseEntityDescriptor.store = this.store;
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
