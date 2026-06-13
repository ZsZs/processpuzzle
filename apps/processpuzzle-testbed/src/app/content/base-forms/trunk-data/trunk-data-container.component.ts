import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { TrunkDataStore } from './trunk-data.store';
import { MarkdownComponent } from 'ngx-markdown';
import { createTrunkDataDescriptor } from './trunk-data.descriptors';

@Component({
  selector: 'trunk-data',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, BaseEntityContainerComponent],
  templateUrl: 'trunk-data-container.component.html',
  styles: ``,
})
export class TrunkDataContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(TrunkDataStore);
  entityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.entityDescriptor = createTrunkDataDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.entityTitle = () => this.store.currentEntity()?.key ?? '';
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
