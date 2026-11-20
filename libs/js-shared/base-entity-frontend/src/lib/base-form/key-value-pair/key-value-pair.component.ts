import { Component, computed, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { ColorPickerComponent } from '@processpuzzle/util';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

interface KeyValueEntry {
  key: string;
  value: string;
}

@Component({
  selector: 'base-key-value-pair',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list" (click)="enterEditMode()">
            @for (entry of entries(); track entry.key) {
              <li>
                <span class="entry">
                  <span class="key">{{ labelOf(entry.key) }}</span
                  >: <span class="value">{{ entry.value }}</span>
                </span>
                <button
                  type="button"
                  mat-icon-button
                  class="base-entity-form-delete-button"
                  [attr.aria-label]="'remove ' + entry.key"
                  (click)="remove(entry.key); $event.stopPropagation()"
                >
                  <mat-icon>cancel</mat-icon>
                </button>
              </li>
            }
          </ul>
          @if (editing()) {
            <div class="edit-row">
              @if (allowed().length) {
                <mat-form-field>
                  <mat-label>key</mat-label>
                  <mat-select data-testid="property-key" [value]="selectedKey()" (selectionChange)="selectKey($event.value)">
                    @for (property of allowed(); track property.key) {
                      <mat-option [value]="property.key">{{ property.label ?? property.key }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                @if (valuesOfSelected().length) {
                  <mat-form-field>
                    <mat-label>value</mat-label>
                    <mat-select data-testid="property-value" [value]="selectedValue()" (selectionChange)="selectedValue.set($event.value)">
                      @for (value of valuesOfSelected(); track value) {
                        <mat-option [value]="value">{{ value }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                } @else if (editorOfSelected() === 'color') {
                  <pp-color-picker data-testid="property-value" [label]="selectedLabel()" [(value)]="selectedValue" />
                } @else {
                  <mat-form-field>
                    <mat-label>value</mat-label>
                    <input matInput #chosenValue data-testid="property-value" [value]="selectedValue()" (input)="selectedValue.set(chosenValue.value)" (keydown.enter)="addSelected()" />
                  </mat-form-field>
                }
                <button mat-icon-button type="button" aria-label="add property" [disabled]="!selectedKey()" (click)="addSelected()">
                  <mat-icon>add</mat-icon>
                </button>
              } @else {
                <mat-form-field>
                  <mat-label>key</mat-label>
                  <input matInput #keyInput (keydown.enter)="valueInput.focus()" />
                </mat-form-field>
                <mat-form-field>
                  <mat-label>value</mat-label>
                  <input matInput #valueInput (keydown.enter)="add(keyInput, valueInput)" />
                </mat-form-field>
                <button mat-icon-button type="button" aria-label="add property" (click)="add(keyInput, valueInput)">
                  <mat-icon>add</mat-icon>
                </button>
              }
              <button mat-icon-button type="button" aria-label="cancel edit" (click)="cancelEdit()">
                <mat-icon>close</mat-icon>
              </button>
            </div>
          }
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      .base-entity-form-list {
        list-style: none;
        padding-left: 0;
        cursor: pointer;
      }
      .base-entity-form-list li {
        display: flex;
        align-items: center;
      }
      .base-entity-form-list .base-entity-form-delete-button {
        margin-left: auto;
      }
      .base-entity-form-list .key {
        font-weight: 500;
      }
      .edit-row {
        display: flex;
        align-items: center;
        gap: 8px;
      }
    `,
  ],
  imports: [NgClass, NgStyle, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatSelect, MatOption, MatIconButton, ColorPickerComponent, MatIcon, EntityLabelPipe],
})
export class KeyValuePairComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  readonly editing = signal(false);
  readonly selectedKey = signal('');
  readonly selectedValue = signal('');
  readonly allowed = computed(() => this.config().allowedProperties ?? []);
  readonly valuesOfSelected = computed(() => this.allowed().find((property) => property.key === this.selectedKey())?.values ?? []);

  readonly editorOfSelected = computed(() => this.allowed().find((property) => property.key === this.selectedKey())?.valueEditor);
  readonly selectedLabel = computed(() => this.labelOf(this.selectedKey()) || 'value');

  /** The name a user knows the property by: its label when the descriptor gives one, else the key. */
  labelOf(key: string): string {
    return this.allowed().find((property) => property.key === key)?.label ?? key;
  }

  selectKey(key: string): void {
    this.selectedKey.set(key);
    this.selectedValue.set('');
  }

  addSelected(): void {
    const key = this.selectedKey();
    if (!key) return;
    this.setRecord({ ...this.record(), [key]: this.selectedValue().trim() });
    this.selectedKey.set('');
    this.selectedValue.set('');
  }

  enterEditMode(): void {
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  add(keyInput: HTMLInputElement, valueInput: HTMLInputElement): void {
    const key = (keyInput.value || '').trim();
    const value = (valueInput.value || '').trim();
    if (!key) {
      keyInput.focus();
      return;
    }

    this.setRecord({ ...this.record(), [key]: value });
    keyInput.value = '';
    valueInput.value = '';
    keyInput.focus();
  }

  remove(key: string): void {
    const next = { ...this.record() };
    delete next[key];
    this.setRecord(next);
  }

  entries(): KeyValueEntry[] {
    const record = this.record();
    return Object.keys(record).map((key) => ({ key, value: record[key] }));
  }

  private record(): Record<string, string> {
    const value = this.formGroup.get(this.config().attrName)?.value;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return value as Record<string, string>;
    }
    return {};
  }

  private setRecord(record: Record<string, string>): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(record);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
