import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatChipEditedEvent, MatChipGrid, MatChipInput, MatChipInputEvent, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatIcon } from '@angular/material/icon';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { TAG_SUGGESTION_SOURCES } from './tag-suggestions';

@Component({
  selector: 'base-tags',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <mat-form-field>
          <mat-label>{{ config().i18nKey() | ppLabel: config().label }}</mat-label>
          <mat-chip-grid #chipGrid [id]="config().attrName">
            @for (tag of tags(); track $index; let i = $index) {
              <mat-chip-row [editable]="true" [class.pp-tag-new]="isNew(tag)" [attr.title]="isNew(tag) ? 'New value' : null" (edited)="edit(i, $event)" (removed)="remove(i)">
                {{ tag }}
                <button matChipRemove [attr.aria-label]="'remove ' + tag">
                  <mat-icon>cancel</mat-icon>
                </button>
              </mat-chip-row>
            }
            <input
              [matChipInputFor]="chipGrid"
              [matAutocomplete]="auto"
              [matChipInputSeparatorKeyCodes]="separatorKeysCodes"
              (input)="typed.set(tagInput.value)"
              (matChipInputTokenEnd)="add($event)"
              #tagInput
            />
          </mat-chip-grid>
          <mat-autocomplete #auto="matAutocomplete" (optionSelected)="pick($event, tagInput)">
            @for (option of options(); track option) {
              <mat-option [value]="option">{{ option }}</mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
      /* Material defaults the chip input to a 150px flex-basis, which wraps it
         onto its own (empty-looking) line once a chip is present. Shrink the
         basis so it stays on the chips' line; flex-grow still fills the rest. */
      input.mat-mdc-chip-input {
        flex-basis: 30px;
      }
      /* Brand the chips light green; consumers can retarget via --pp-chip-*. */
      /* A value the suggestion source does not know: dashed, so the author sees it is about to be created. */
      mat-chip-row.pp-tag-new {
        outline: 1px dashed var(--pp-chip-text);
        outline-offset: -2px;
      }
      mat-chip-row {
        --mat-chip-elevated-container-color: var(--pp-chip-bg);
        --mat-chip-label-text-color: var(--pp-chip-text);
        --mat-chip-with-trailing-icon-trailing-icon-color: var(--pp-chip-text);
      }
    `,
  ],
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatChipGrid, MatChipRow, MatChipInput, MatChipRemove, MatIcon, MatAutocomplete, MatAutocompleteTrigger, MatOption, EntityLabelPipe],
})
export class TagsComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly separatorKeysCodes: number[] = [ENTER, COMMA];
  readonly typed = signal('');

  private readonly sources = inject(TAG_SUGGESTION_SOURCES, { optional: true }) ?? [];
  private readonly destroyRef = inject(DestroyRef);
  private readonly suggested = signal<readonly string[]>([]);
  /** What the attribute's suggestion source offers; empty for free text, and when the source fails. */
  readonly suggestions = this.suggested.asReadonly();

  ngOnInit(): void {
    const source = this.sources.find((candidate) => candidate.key === this.config().tagSuggestions);
    source
      ?.suggestions()
      .pipe(
        catchError(() => of([] as readonly string[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((values) => this.suggested.set(values));
  }

  /** The suggestions not yet picked that match what is being typed. */
  options(): string[] {
    const picked = new Set(this.tags());
    const typed = this.typed().trim().toLowerCase();
    return this.suggestions().filter((option) => !picked.has(option) && option.toLowerCase().includes(typed));
  }

  /** Whether the suggestion source is in play and does not know `tag`. */
  isNew(tag: string): boolean {
    const known = this.suggestions();
    return known.length > 0 && !known.includes(tag);
  }

  pick(event: MatAutocompleteSelectedEvent, input: HTMLInputElement): void {
    this.setTags([...this.tags(), event.option.value]);
    input.value = '';
    this.typed.set('');
  }

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
