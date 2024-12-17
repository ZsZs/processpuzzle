import { Component, effect, inject, input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityTabsComponent } from './base-tabs/base-entity-tabs.component';
import { BaseEntityDescriptor } from './base-entity/base-entity.descriptor';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'base-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityTabsComponent],
  template: `
    <div>
      <base-entity-tabs [baseEntityListOptions]="baseEntityListOptions()"></base-entity-tabs>
    </div>
  `,
  styles: ``,
})
export class BaseEntityContainerComponent implements OnDestroy, OnInit {
  baseEntityListOptions = input.required<BaseEntityDescriptor>();
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
    this.store = this.baseEntityListOptions().store;
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
