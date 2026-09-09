import { Component, computed, effect, inject, Signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterOutlet } from '@angular/router';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { ACTIVE_ENTITY_FACADE } from '../base-entity-facade/active-entity-facade.token';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';

/**
 * The branch route of an embedded level: it hands the level's descriptor to the form below it and adds
 * nothing else.
 *
 * Where a stand-alone entity gets `BaseEntityContainerComponent` — tab bar, toolbar, status bar — an
 * embedded level deliberately gets none of that. Its rows are already listed on the form of the entity
 * that contains it, so a list tab would be a second way to the same thing, and a second tab bar and
 * status bar inside the owner's would say twice what the breadcrumb in
 * `BaseEntityStatusbarComponent` says once. Drilling down replaces the surface; it does not nest chrome
 * inside chrome.
 */
@Component({
  selector: 'base-embedded-entity-host',
  standalone: true,
  imports: [RouterOutlet],
  template: ` <router-outlet [routerOutletData]="entityDescriptor()" /> `,
})
export class EmbeddedEntityHostComponent {
  private readonly facade = inject(ACTIVE_ENTITY_FACADE);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly entityDescriptor: Signal<BaseEntityDescriptor> = computed(() => this.facade.descriptor);

  constructor() {
    this.registerEffects();
  }

  // region protected, private helper methods
  /**
   * An embedded write is a write of the containing document, and it fails the same way any other write
   * can — so the level reports it, even though it has no toolbar of its own.
   *
   * Unlike `BaseEntityContainerComponent` this host does **not** reset the store when it is destroyed:
   * an embedded store's rows are a projection of the owner's document, and emptying them on the way back
   * up would blank the very row list the user returns to.
   */
  private registerEffects(): void {
    effect(() => {
      const store = this.entityDescriptor().store as BaseEntityStoreApi<BaseEntity> | undefined;
      const errorValue = store?.error();
      if (errorValue) this.snackBar.open(errorValue, 'Close', { duration: 5000 });
    });
  }
  // endregion
}
