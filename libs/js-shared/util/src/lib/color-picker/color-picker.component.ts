import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { MatInput } from '@angular/material/input';

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
 * A color value: a swatch that opens the browser's color picker, and a text field beside it for what the picker
 * cannot say — a color name, `rgb(…)`, `var(--pp-color-white)`. The two edit one value, which is the text; picking
 * a color writes its `#rrggbb` into the field.
 */
@Component({
  selector: 'pp-color-picker',
  standalone: true,
  imports: [MatInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="pp-color-picker">
      <input
        type="color"
        class="pp-color-picker__swatch"
        data-testid="color-swatch"
        [attr.aria-label]="label()"
        [value]="swatch()"
        (input)="value.set($any($event.target).value)"
      />
      <input
        matInput
        class="pp-color-picker__text"
        data-testid="color-text"
        [attr.aria-label]="label()"
        [value]="value()"
        (input)="value.set($any($event.target).value)"
      />
    </span>
  `,
  styles: `
    .pp-color-picker {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .pp-color-picker__swatch {
      width: 36px;
      height: 28px;
      padding: 0;
      border: 1px solid rgba(0, 0, 0, 0.3);
      border-radius: 4px;
      background: none;
      cursor: pointer;
    }
    .pp-color-picker__text {
      flex: 1;
      min-width: 0;
    }
  `,
})
export class ColorPickerComponent {
  readonly value = model('');
  readonly label = input('color');
  protected readonly swatch = computed(() => swatchColor(this.value()));
}
