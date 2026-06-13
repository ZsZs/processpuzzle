import { Component, computed, input, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';

@Component({
  selector: 'base-entity-statusbar',
  standalone: true,
  imports: [CommonModule, MatToolbar],
  template: `
    <mat-toolbar *ngIf="isVisible()">
      <div>
        <span>{{ entityTitle() }}</span>
      </div>
    </mat-toolbar>
  `,
})
export class BaseEntityStatusbarComponent implements OnInit {
  store!: BaseEntityStoreApi<BaseEntity>;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  entityTitle: Signal<string> = computed<string>(() => this.evaluateEntityTitle(this.entityDescriptor().entityTitle));
  isVisible: Signal<boolean> = computed(() => this.store != null && (this.store.currentEntity() !== undefined || this.store.selectedEntities().length === 1));

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<BaseEntity>;
  }

  // endregion

  // event handling methods
  // endregion

  // protected, private helper methods
  private evaluateEntityTitle(title: string | (() => string)): string {
    return typeof title === 'function' ? title() : title;
  }

  // endregion
}
