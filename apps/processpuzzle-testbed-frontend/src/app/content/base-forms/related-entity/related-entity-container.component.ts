import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { MarkdownComponent } from 'ngx-markdown';
import { RelatedEntityStore } from './related-entity.store';
import { createRelatedEntityDescriptor } from './related-entity.descriptors';

@Component({
  selector: 'related-entity',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, BaseEntityContainerComponent],
  templateUrl: 'related-entity-container.component.html',
  styles: ``,
})
export class RelatedEntityContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(RelatedEntityStore);
  entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createRelatedEntityDescriptor();
    this.entityDescriptor.store = this.store;
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
