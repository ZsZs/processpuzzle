import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatChipEditedEvent, MatChipGrid, MatChipInput, MatChipInputEvent, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'base-tags',
  standalone: true,
  template: `
    <ng-container *ngIf="config().visible">
      <div class="row" [formGroup]="formGroup">
        <mat-form-field>
          <mat-label>{{ config().label }}</mat-label>
          <mat-chip-grid #chipGrid [id]="config().attrName">
            @for (tag of tags(); track $index; let i = $index) {
              <mat-chip-row [editable]="true" (edited)="edit(i, $event)" (removed)="remove(i)">
                {{ tag }}
                <button matChipRemove [attr.aria-label]="'remove ' + tag">
                  <mat-icon>cancel</mat-icon>
                </button>
              </mat-chip-row>
            }
            <input [placeholder]="config().label" [matChipInputFor]="chipGrid" [matChipInputSeparatorKeyCodes]="separatorKeysCodes" (matChipInputTokenEnd)="add($event)" />
          </mat-chip-grid>
        </mat-form-field>
      </div>
    </ng-container>
  `,
  imports: [NgIf, ReactiveFormsModule, MatFormField, MatLabel, MatChipGrid, MatChipRow, MatChipInput, MatChipRemove, MatIcon],
})
export class TagsComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  readonly separatorKeysCodes: number[] = [ENTER, COMMA];

  add(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();
    if (!value) {
      event.chipInput?.clear();
      return;
    }

    this.setTags([...this.tags(), value]);
    event.chipInput?.clear();
  }

  remove(index: number): void {
    const updatedTags = [...this.tags()];
    updatedTags.splice(index, 1);
    this.setTags(updatedTags);
  }

  edit(index: number, event: MatChipEditedEvent): void {
    const value = (event.value || '').trim();
    if (!value) {
      this.remove(index);
      return;
    }

    const updatedTags = [...this.tags()];
    updatedTags[index] = value;
    this.setTags(updatedTags);
  }

  tags(): string[] {
    const value = this.formGroup.get(this.config().attrName)?.value;
    return Array.isArray(value) ? value : [];
  }

  private setTags(tags: string[]): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(tags);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
