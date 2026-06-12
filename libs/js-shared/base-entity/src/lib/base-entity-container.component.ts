import { Component, computed, effect, inject, input, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import { BaseEntityTabsComponent } from './base-tabs/base-entity-tabs.component';
import { BaseEntityDescriptor } from './base-entity/base-entity.descriptor';
import { BaseEntityFacade } from './base-entity-facade/base-entity-facade';
import { ACTIVE_ENTITY_FACADE } from './base-entity-facade/active-entity-facade.token';

@Component({
  selector: 'base-entity-container',
  standalone: true,
  imports: [CommonModule, BaseEntityTabsComponent],
  template: `
    <ng-content></ng-content>
    <div>
      <base-entity-tabs [entityDescriptor]="resolvedDescriptor()"></base-entity-tabs>
    </div>
  `,
})
export class BaseEntityContainerComponent implements OnDestroy, OnInit {
  entityDescriptor = input<BaseEntityDescriptor | undefined>(undefined);
  facade = input<BaseEntityFacade<any> | undefined>(undefined);

  private readonly injectedFacade = inject(ACTIVE_ENTITY_FACADE, { optional: true });
  protected snackBar = inject<MatSnackBar>(MatSnackBar);
  store: any;

  protected activeFacade = computed<BaseEntityFacade<any> | undefined>(() => this.facade() ?? this.injectedFacade ?? undefined);

  protected resolvedDescriptor = computed<BaseEntityDescriptor>(() => {
    const f = this.activeFacade();
    if (f) return f.descriptor;
    const descriptor = this.entityDescriptor();
    if (!descriptor) {
      throw new Error('BaseEntityContainerComponent requires either [facade], ACTIVE_ENTITY_FACADE provider, or [entityDescriptor]');
    }
    return descriptor;
  });

  constructor() {
    this.registerEffects();
  }

  // region Angular lifecycle hooks
  ngOnDestroy() {
    this.store?.reset?.();
  }

  ngOnInit() {
    this.store = this.activeFacade()?.store ?? this.resolvedDescriptor().store;
  }

  // endregion

  // region protected, private helper methods
  private registerEffects() {
    effect(() => {
      if (this.store?.error?.()) {
        const errorMessage = this.store.error() ? this.store.error() : 'Undefined Error';
        this.snackBar.open(errorMessage, 'Close', { duration: 5000 });
      }
    });
  }

  // endregion
}
