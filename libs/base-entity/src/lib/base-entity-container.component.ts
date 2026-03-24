import { Component, effect, inject, input, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import { BaseEntityTabsComponent } from './base-tabs/base-entity-tabs.component';
import { BaseEntityDescriptor } from './base-entity/base-entity.descriptor';

@Component({
  selector: 'base-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityTabsComponent],
  template: `
    <div>
      <base-entity-tabs [entityDescriptor]="entityDescriptor()"></base-entity-tabs>
    </div>
  `,
})
export class BaseEntityContainerComponent implements OnDestroy, OnInit {
  entityDescriptor = input.required<BaseEntityDescriptor>();
  protected snackBar = inject<MatSnackBar>(MatSnackBar);
  store: any;

  constructor() {
    this.registerEffects();
  }

  // region Angular lifecycle hooks
  ngOnDestroy() {
    this.store.reset();
  }

  ngOnInit() {
    this.store = this.entityDescriptor().store;
  }

  // endregion

  // region protected, private helper methods
  private registerEffects() {
    effect(() => {
      if (this.store.error()) {
        const errorMessage = this.store.error() ? this.store.error() : 'Undefined Error';
        this.snackBar.open(errorMessage, 'Close', { duration: 5000 });
      }
    });
  }

  // endregion
}
