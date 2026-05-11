import { Component, computed, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { BaseEntity } from '../../base-entity/base-entity';
import { LookupTable } from './lookup-table';
import { NgIf } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

interface LookupStore {
  entities?: () => LookupTable[];
  load?: (query: Record<string, unknown>) => unknown;
  loadById?: (id: string) => LookupTable | undefined;
}

@Component({
  selector: 'lookup-control',
  standalone: true,
  template: `
    <ng-container *ngIf="config().visible">
      <div class="row" [formGroup]="formGroup">
        <mat-form-field>
          <mat-label>{{ config().label }}</mat-label>
          <input type="text" matInput [formControl]="displayControl" [matAutocomplete]="lookupOptions" (blur)="restoreSelectedValue()" />
          <button type="button" mat-icon-button matSuffix [disabled]="!canNavigateToRelated()" (click)="navigateToRelated()" aria-label="Related entity">
            <mat-icon>link</mat-icon>
          </button>
          <mat-autocomplete #lookupOptions="matAutocomplete" (optionSelected)="selectLookupItem($event)">
            @for (lookupItem of filteredLookupItems(); track lookupItem.key) {
              <mat-option [value]="lookupItem.key">{{ lookupDisplayValue(lookupItem) }}</mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
        <input type="hidden" [formControlName]="config().attrName" />
      </div>
    </ng-container>
  `,
  imports: [NgIf, ReactiveFormsModule, MatLabel, MatFormField, MatInput, MatAutocompleteModule, MatIconButton, MatIcon, MatSuffix],
})
export class LookupComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly displayControl = new FormControl<string>('', { nonNullable: true });
  readonly filterText = signal('');
  private readonly destroyRef = inject(DestroyRef);

  private readonly lookupStore = computed<LookupStore | undefined>(() => this.resolveRelatedEntityStore<LookupStore>());
  readonly lookupItems = computed<LookupTable[]>(() => this.lookupStore()?.entities?.() ?? []);
  readonly filteredLookupItems = computed<LookupTable[]>(() => {
    const filterText = this.filterText().trim().toLocaleLowerCase();
    const lookupItems = this.lookupItems();
    if (!filterText) {
      return lookupItems;
    }

    return lookupItems.filter((lookupItem) => this.lookupFilterValue(lookupItem).includes(filterText));
  });

  constructor() {
    super();
    effect(() => {
      const disabled = this.config().disabled;
      if (disabled && this.displayControl.enabled) {
        this.displayControl.disable({ emitEvent: false });
      } else if (!disabled && this.displayControl.disabled) {
        this.displayControl.enable({ emitEvent: false });
      }

      const selectedItem = this.selectedLookupItem();
      const displayValue = selectedItem ? this.lookupDisplayValue(selectedItem) : this.lookupKey();
      if (displayValue !== this.displayControl.value) {
        this.displayControl.setValue(displayValue, { emitEvent: false });
      }
      this.filterText.set(displayValue);
    });
  }

  ngOnInit(): void {
    this.loadLookupTable();
    this.displayControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((displayValue) => {
      this.filterText.set(displayValue ?? '');
      if (displayValue === '') {
        this.setLookupKey('');
      }
    });
  }

  selectLookupItem(event: MatAutocompleteSelectedEvent): void {
    this.setLookupKey(String(event.option.value ?? ''));
    this.restoreSelectedValue();
  }

  restoreSelectedValue(): void {
    const selectedItem = this.selectedLookupItem();
    const displayValue = selectedItem ? this.lookupDisplayValue(selectedItem) : this.lookupKey();
    this.displayControl.setValue(displayValue, { emitEvent: false });
    this.filterText.set(displayValue);
  }

  navigateToRelated(): void {
    const relatedEntityName = this.config().linkedEntityType?.entityName;
    const relatedEntityId = this.selectedLookupItem()?.id ?? this.lookupKey();
    if (!relatedEntityName || !relatedEntityId) {
      return;
    }

    this.formNavigator.navigateToRelated(relatedEntityName, relatedEntityId, this.formNavigator.determineCurrentUrl());
  }

  canNavigateToRelated(): boolean {
    return !!this.config().linkedEntityType?.entityName && !!(this.selectedLookupItem()?.id ?? this.lookupKey());
  }

  lookupDisplayValue(lookupItem: LookupTable): string {
    return lookupItem.value === undefined || lookupItem.value === null ? lookupItem.key : String(lookupItem.value);
  }

  private selectedLookupItem(): LookupTable | undefined {
    const lookupKey = this.lookupKey();
    if (!lookupKey) {
      return undefined;
    }

    return this.lookupItems().find((lookupItem) => lookupItem.key === lookupKey || lookupItem.id === lookupKey) ?? this.lookupStore()?.loadById?.(lookupKey);
  }

  private lookupKey(): string {
    const controlValue = this.formGroup?.get(this.config().attrName)?.value;
    const value = controlValue ?? this.value();
    return value === undefined || value === null ? '' : String(value);
  }

  private setLookupKey(lookupKey: string): void {
    const attrName = this.config().attrName;
    const control = this.formGroup.get(attrName);
    if (!control) {
      return;
    }

    control.setValue(lookupKey);
    control.markAsDirty();
    control.markAsTouched();
    Reflect.set(this.entity() as Record<string, unknown>, attrName, lookupKey);
    this.formGroup.markAsDirty();
    this.formGroup.markAsTouched();
  }

  private lookupFilterValue(lookupItem: LookupTable): string {
    return [lookupItem.key, lookupItem.value, lookupItem.description]
      .filter((value): value is string | number => value !== undefined && value !== null)
      .map((value) => String(value).toLocaleLowerCase())
      .join(' ');
  }

  private loadLookupTable(): void {
    const lookupStore = this.lookupStore();
    if (!lookupStore?.load) {
      return;
    }

    lookupStore.load({});
  }
}
