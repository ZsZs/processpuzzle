import { Component, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';

interface KeyValueEntry {
  key: string;
  value: string;
}

@Component({
  selector: 'base-additional-properties',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list" (click)="enterEditMode()">
            @for (entry of entries(); track entry.key) {
              <li>
                <span class="entry">
                  <span class="key">{{ entry.key }}</span
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
  imports: [NgClass, NgStyle, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatIconButton, MatIcon],
})
export class AdditionalPropertiesComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  readonly editing = signal(false);

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
