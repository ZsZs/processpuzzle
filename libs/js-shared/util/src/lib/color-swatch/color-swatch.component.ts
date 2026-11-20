import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/** `#rgb` or `#rrggbb`, the only forms a native color input can show. */
const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;

/**
 * The `#rrggbb` a native color input needs for `value`: a hex color as is (a short one expanded), anything else —
 * a name such as `rebeccapurple`, `rgb(…)`, `var(--x)` — black, because the swatch cannot show it.
 */
export function swatchColor(value: string): string {
  const hex = value.trim();
  if (!HEX_COLOR.test(hex)) return '#000000';
  return hex.length === 4 ? `#${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}`.toLowerCase() : hex.toLowerCase();
}

/**
 * A swatch that opens the browser's color picker and writes the picked `#rrggbb` into `value`.
 *
 * It is only the picker, not a field: put it as a `matPrefix` (or `matSuffix`) of a `mat-form-field` whose own
 * `matInput` holds the same value as text, so that the color shares the field — its label, border and height — with the
 * text that says what a swatch cannot: a color name, `rgb(…)`, `var(--pp-color-white)`.
 */
@Component({
  selector: 'pp-color-swatch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <input type="color" class="pp-color-swatch__input" data-testid="color-swatch" [attr.aria-label]="label()" [value]="swatch()" (input)="value.set($any($event.target).value)" />
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      margin-right: 8px;
    }
    .pp-color-swatch__input {
      width: 28px;
      height: 24px;
      padding: 0;
      border: 1px solid rgba(0, 0, 0, 0.3);
      border-radius: 4px;
      background: none;
      cursor: pointer;
    }
  `,
})
export class ColorSwatchComponent {
  readonly value = model('');
  readonly label = input('color');
  protected readonly swatch = computed(() => swatchColor(this.value()));
}
