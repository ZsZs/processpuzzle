import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass } from '@angular/common';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { AiAssistButtonComponent } from '../ai-assist/ai-assist-button.component';

@Component({
  selector: 'base-textarea',
  standalone: true,
  templateUrl: './textarea.component.html',
  imports: [NgClass, MatFormField, MatInput, MatLabel, MatSuffix, ReactiveFormsModule, EntityLabelPipe, AiAssistButtonComponent],
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
    `,
  ],
})
export class TextareaComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly formValues = signal<Record<string, unknown>>({});

  /** The attribute's AI assist, when it declares one. */
  protected readonly assist = computed(() => this.config().aiAssist);
  /** The description the AI writes this value from. */
  protected readonly assistText = computed(() => this.textOf(this.assist()?.from));
  /** The other form values the AI task asks for, left out while blank. */
  protected readonly assistParameters = computed(() => {
    const entries = (this.assist()?.parameters ?? []).map((name) => [name, this.textOf(name)] as const).filter(([, value]) => value.trim().length > 0);
    return Object.fromEntries(entries);
  });
  protected readonly assistCurrent = computed(() => this.textOf(this.config().attrName));

  ngOnInit(): void {
    this.formValues.set(this.formGroup.getRawValue());
    this.formGroup.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.formValues.set(this.formGroup.getRawValue()));
  }

  /** Writes what the AI generated, or what the user took back, into the field; the user still saves the form. */
  protected onAssistGenerated(value: string): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(value);
    control?.markAsDirty();
  }

  private textOf(name: string | undefined): string {
    const value = name ? this.formValues()[name] : undefined;
    return typeof value === 'string' ? value : '';
  }
}
