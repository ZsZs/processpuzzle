import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { FirestoreDocStore } from './firestore-doc.store';
import { firestoreDocDescriptors } from './firestore-doc.descriptor';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'firestore-doc',
  standalone: true,
  imports: [CommonModule, MarkdownComponent, BaseEntityContainerComponent],
  templateUrl: 'firestore-doc-container.component.html',
  styles: ``,
})
export class FirestoreDocContainerComponent implements OnDestroy {
  private containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private store = inject(FirestoreDocStore);
  baseEntityListOptions: BaseEntityDescriptor;

  constructor() {
    this.baseEntityListOptions = {
      entityName: 'Firestore Doc',
      store: this.store,
      attrDescriptors: firestoreDocDescriptors,
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
