import { Component, computed, inject, input, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';

@Component({
  selector: 'base-entity-statusbar',
  standalone: true,
  imports: [CommonModule, MatToolbar, MatIcon],
  template: `
    <mat-toolbar *ngIf="isVisible()">
      <div>
        <span>{{ entityTitle() }}</span>
      </div>
      <span class="statusbar-spacer"></span>
      <div class="rule-summary" *ngIf="showViolationSummary()">
        <span class="chip severity-error" *ngIf="errorCount() > 0">
          <mat-icon>error</mat-icon>
          <span>{{ errorCount() }}</span>
        </span>
        <span class="chip severity-warning" *ngIf="warningCount() > 0">
          <mat-icon>warning</mat-icon>
          <span>{{ warningCount() }}</span>
        </span>
        <span class="chip severity-info" *ngIf="infoCount() > 0">
          <mat-icon>info</mat-icon>
          <span>{{ infoCount() }}</span>
        </span>
      </div>
    </mat-toolbar>
  `,
  styles: [
    `
      .statusbar-spacer {
        flex: 1 1 auto;
      }
      .rule-summary {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 13px;
        line-height: 1;
      }
      .chip mat-icon {
        width: 16px;
        height: 16px;
        font-size: 16px;
        line-height: 16px;
      }
      .chip.severity-error {
        background-color: rgba(211, 47, 47, 0.15);
        color: #b71c1c;
      }
      .chip.severity-warning {
        background-color: rgba(245, 124, 0, 0.15);
        color: #b26a00;
      }
      .chip.severity-info {
        background-color: rgba(25, 118, 210, 0.15);
        color: #0d47a1;
      }
    `,
  ],
})
export class BaseEntityStatusbarComponent implements OnInit {
  store!: BaseEntityStoreApi<BaseEntity>;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  entityTitle: Signal<string> = computed<string>(() => this.evaluateEntityTitle(this.entityDescriptor().entityTitle));
  isVisible: Signal<boolean> = computed(() => this.store != null && (this.store.currentEntity() !== undefined || this.store.selectedEntities().length === 1));

  private readonly violationsStore = inject(RuleViolationsSingletonStore);
  errorCount = this.violationsStore.errorCount;
  warningCount = this.violationsStore.warningCount;
  infoCount = this.violationsStore.infoCount;
  showViolationSummary: Signal<boolean> = computed(() => this.violationsStore.entityName() === this.entityDescriptor().entityName && this.violationsStore.hasViolations());

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
