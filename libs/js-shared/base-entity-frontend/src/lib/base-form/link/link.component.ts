import { Component, signal } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatTooltip } from '@angular/material/tooltip';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { BaseFormControlComponent } from '../base-form-control.component';

/**
 * The address a link value opens: the trimmed text when it is an absolute http or https address, otherwise
 * `undefined`. Other schemes (`javascript:`, `mailto:` …) are never offered as a link, whatever was typed.
 */
export function openableUrl(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const text = value.trim();
  if (!text) return undefined;
  try {
    const url = new URL(text);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}

/**
 * A `LINK` attribute: a text field that shows its content as a link. Unfocused, the text is underlined and
 * cannot be typed over; focusing it (by tabbing in or clicking it) makes it editable, and leaving it makes it
 * a link again. The open icon, offered once the text is an http or https address, opens it in a new tab.
 */
@Component({
  selector: 'base-link',
  standalone: true,
  imports: [MatFormField, MatInput, MatLabel, MatSuffix, MatIcon, MatTooltip, FormsModule, ReactiveFormsModule, EntityLabelPipe, TranslocoPipe],
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <mat-form-field>
          <mat-label [attr.for]="config().attrName">{{ config().i18nKey() | ppLabel: config().label }}</mat-label>
          <input
            matInput
            type="url"
            inputmode="url"
            [id]="config().attrName"
            formControlName="{{ config().attrName }}"
            [readonly]="!editing()"
            [class.pp-link--display]="!editing()"
            [placeholder]="config().placeholder ?? ''"
            data-testid="link-input"
            (focus)="startEditing()"
            (blur)="stopEditing()"
          />
          @if (openUrl(); as url) {
            <a
              matSuffix
              class="pp-link__open"
              data-testid="link-open"
              [href]="url"
              target="_blank"
              rel="noopener noreferrer"
              [matTooltip]="'base_entity.link.open' | transloco"
              [attr.aria-label]="'base_entity.link.open' | transloco"
            >
              <mat-icon>open_in_new</mat-icon>
            </a>
          }
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
      input.pp-link--display {
        color: var(--mat-sys-primary, #1a73e8);
        text-decoration: underline;
        cursor: pointer;
      }
      .pp-link__open {
        display: inline-flex;
        color: inherit;
        cursor: pointer;
      }
    `,
  ],
})
export class LinkComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  /** True while the field has the focus; only then can the text be typed over. */
  readonly editing = signal(false);

  /** The address the current text opens, or `undefined` when it is not an http or https address. */
  openUrl(): string | undefined {
    return openableUrl(this.formGroup?.get(this.config().attrName)?.value);
  }

  startEditing(): void {
    this.editing.set(true);
  }

  stopEditing(): void {
    this.editing.set(false);
  }
}
