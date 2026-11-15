import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity } from '../base-entity/base-entity';
import { createTestId } from '../base-entity/base-entity-utility';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { contextLabel, valueAt } from './working-context-attributes';
import { WorkingContextService } from './working-context.service';

interface PickerRow {
  id: string;
  label: string;
  isActive: boolean;
}

/**
 * Picks the entity of a working context: a search over the context's `picker.searchFields` with the recently
 * active entities on top. Used inline by the empty state and inside the status bar's drawer, so it is a light
 * list of its own rather than the entity's full List view, which is bound to the router outlet it is shown in.
 */
@Component({
  selector: 'pp-working-context-picker',
  standalone: true,
  imports: [MatFormField, MatLabel, MatSuffix, MatInput, MatIcon, TranslocoPipe],
  template: `
    <mat-form-field class="picker-search" subscriptSizing="dynamic">
      <mat-label>{{ 'base_entity.working_context.search' | transloco }}</mat-label>
      <input matInput type="search" [attr.data-testid]="testId('search')" [value]="searchKey()" (input)="onSearch($event)" />
      <mat-icon matSuffix>search</mat-icon>
    </mat-form-field>

    @if (recentRows().length > 0) {
      <h3 class="picker-heading">{{ 'base_entity.working_context.recent' | transloco }}</h3>
      <ul class="picker-list" [attr.data-testid]="testId('recent')">
        @for (row of recentRows(); track row.id) {
          <li>
            <button type="button" class="picker-row" [class.active]="row.isActive" [attr.data-testid]="testId('recent-row')" (click)="onPick(row.id)">{{ row.label }}</button>
          </li>
        }
      </ul>
    }

    <h3 class="picker-heading">{{ 'base_entity.working_context.all' | transloco }}</h3>
    <ul class="picker-list" [attr.data-testid]="testId('rows')">
      @for (row of rows(); track row.id) {
        <li>
          <button type="button" class="picker-row" [class.active]="row.isActive" [attr.data-testid]="testId('row')" (click)="onPick(row.id)">{{ row.label }}</button>
        </li>
      } @empty {
        <li class="picker-empty">{{ 'base_entity.working_context.no_match' | transloco }}</li>
      }
    </ul>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: 260px;
      }
      .picker-heading {
        margin: 8px 0 0;
        font-size: 13px;
        font-weight: 500;
        opacity: 0.7;
      }
      .picker-list {
        margin: 0;
        padding: 0;
        list-style: none;
      }
      .picker-row {
        width: 100%;
        min-height: 48px;
        padding: 0 12px;
        border: none;
        border-radius: 6px;
        background: none;
        color: inherit;
        font: inherit;
        text-align: left;
        cursor: pointer;
      }
      .picker-row:hover,
      .picker-row:focus-visible {
        background-color: rgba(0, 0, 0, 0.06);
      }
      .picker-row.active {
        font-weight: 500;
      }
      .picker-empty {
        padding: 12px;
        opacity: 0.7;
      }
    `,
  ],
})
export class WorkingContextPickerComponent implements OnInit {
  readonly contextName = input.required<string>();
  /** Emits once the selection went through — not when the user declined a confirmation. */
  readonly picked = output<string>();

  protected readonly searchKey = signal('');
  private readonly service = inject(WorkingContextService);
  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly definition = computed(() => this.service.definitionOf(this.contextName()));
  private readonly descriptor = computed(() => this.descriptorRegistry.getDescriptor(this.definition()?.entityName));
  private readonly store = computed(() => this.descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(this.definition()?.entityName));
  private readonly activeId = computed(() => this.service.current(this.contextName())?.id);
  private readonly allRows = computed<PickerRow[]>(() => (this.store()?.entities() ?? []).map((entity) => this.rowOf(entity as Record<string, unknown>)));

  protected readonly rows = computed<PickerRow[]>(() => {
    const key = this.searchKey().trim().toLocaleLowerCase();
    if (key.length === 0) return this.allRows();
    const fields = this.definition()?.picker?.searchFields ?? [this.descriptor()?.titleAttrName() ?? 'id'];
    const entities = this.store()?.entities() ?? [];
    return this.allRows().filter((row) => {
      const entity = entities.find((candidate) => candidate.id === row.id);
      return (
        row.label.toLocaleLowerCase().includes(key) ||
        fields.some((field) =>
          String(valueAt(entity, field, this.descriptor()) ?? '')
            .toLocaleLowerCase()
            .includes(key),
        )
      );
    });
  });

  /** Recent ids the store still holds — a recent entity that has since been deleted is not offered. */
  protected readonly recentRows = computed<PickerRow[]>(() => {
    if (this.searchKey().trim().length > 0) return [];
    const rows = this.allRows();
    return this.service.recent(this.contextName()).flatMap((id) => rows.filter((row) => row.id === id));
  });

  ngOnInit(): void {
    const store = this.store();
    if (store && store.entities().length === 0) store.load({ pathParams: new Map<string, string>(), filters: [] });
  }

  protected onSearch(event: Event): void {
    this.searchKey.set((event.target as HTMLInputElement).value);
  }

  protected async onPick(id: string): Promise<void> {
    if (await this.service.select(this.contextName(), id)) this.picked.emit(id);
  }

  protected testId(suffix: string): string {
    return createTestId(this.definition()?.entityName ?? this.contextName(), 'context-picker-' + suffix);
  }

  private rowOf(entity: Record<string, unknown>): PickerRow {
    const id = String(entity['id']);
    return { id, label: contextLabel(this.definition()?.display?.label, entity, this.descriptor(), id), isActive: id === this.activeId() };
  }
}
