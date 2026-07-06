import { Component, computed, effect, inject, input, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import { BaseEntityTabsComponent } from './base-tabs/base-entity-tabs.component';
import { BaseEntityDescriptor } from './base-entity/base-entity.descriptor';
import { BaseEntityFacade } from './base-entity-facade/base-entity-facade';
import { ACTIVE_ENTITY_FACADE } from './base-entity-facade/active-entity-facade.token';
import { BaseEntity } from './base-entity/base-entity';
import { BaseEntityStoreApi } from './base-entity-store/base-entity.store';

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
  facade = input<BaseEntityFacade<BaseEntity> | undefined>(undefined);

  private readonly injectedFacade = inject(ACTIVE_ENTITY_FACADE, { optional: true });
  protected snackBar = inject<MatSnackBar>(MatSnackBar);
  store!: BaseEntityStoreApi<BaseEntity>;

  protected activeFacade = computed<BaseEntityFacade<BaseEntity> | undefined>(() => this.facade() ?? this.injectedFacade ?? undefined);

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
    this.registerFacadeDescriptorMerge();
  }

  // region Angular lifecycle hooks
  ngOnDestroy() {
    this.store?.reset();
  }

  ngOnInit() {
    this.store = (this.activeFacade()?.store ?? this.resolvedDescriptor().store) as BaseEntityStoreApi<BaseEntity>;
  }

  // endregion

  // region protected, private helper methods
  private registerEffects() {
    effect(() => {
      const errorValue = this.store?.error();
      if (errorValue) {
        this.snackBar.open(errorValue, 'Close', { duration: 5000 });
      }
    });
  }

  private registerFacadeDescriptorMerge() {
    effect(() => {
      const facade = this.activeFacade();
      const input = this.entityDescriptor();
      if (!facade || !input) return;
      const facadeDescriptor = facade.descriptor;
      if (input.entityTitle) facadeDescriptor.entityTitle = input.entityTitle;
      if (input.extraFormActionsTemplate) facadeDescriptor.extraFormActionsTemplate = input.extraFormActionsTemplate;
    });
  }

  // endregion
}
