import { Component, inject, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleChange, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { THEME_COLOR_SCHEMES, THEME_PRESET_SWATCHES, THEME_PRESETS, ThemeColorScheme, ThemePreset, ThemeSelection } from './theme-presets';
import { ThemeService } from './theme.service';

const SCHEME_ICONS: Record<ThemeColorScheme, string> = { light: 'light_mode', dark: 'dark_mode', auto: 'brightness_auto' };

/**
 * Lets the user pick one of the theme presets and a light / dark / auto scheme.
 *
 * It holds no theme of its own: it reads and writes the nearest {@link ThemeService}. In a host application's
 * toolbar that is the root instance, which themes the whole document; inside a base-app shell it is the
 * shell's, which themes that one application and remembers the choice per application.
 */
@Component({
  selector: 'pp-themes-button',
  template: `
    <ng-container *transloco="let t; prefix: 'widgets.themes'">
      <button mat-icon-button [matMenuTriggerFor]="themesMenu" [attr.aria-label]="t('button')" test-id="themes-button">
        <mat-icon>palette</mat-icon>
      </button>
      <mat-menu #themesMenu="matMenu" xPosition="before">
        @for (preset of presets; track preset) {
          <button
            mat-menu-item
            role="menuitemradio"
            [attr.aria-checked]="preset === theme.selection().preset"
            [attr.test-id]="'theme-' + preset"
            (click)="selectPreset(preset)"
          >
            <span class="pp-themes-button__swatch" [style.background]="swatchOf(preset)" aria-hidden="true"></span>
            <span class="pp-themes-button__label">{{ t(preset) }}</span>
            @if (preset === theme.selection().preset) {
              <mat-icon class="pp-themes-button__check">check</mat-icon>
            }
          </button>
        }
        <mat-divider />
        <!-- A click inside the panel closes a mat-menu, which would make the scheme a one-shot toggle. -->
        <div class="pp-themes-button__schemes" role="group" (click)="$event.stopPropagation()" (keydown.enter)="$event.stopPropagation()">
          <mat-button-toggle-group [value]="theme.selection().scheme" (change)="selectScheme($event)" [attr.aria-label]="t('scheme')" hideSingleSelectionIndicator>
            @for (scheme of schemes; track scheme) {
              <mat-button-toggle [value]="scheme" [attr.aria-label]="t(scheme)" [attr.test-id]="'scheme-' + scheme">
                <mat-icon>{{ schemeIcons[scheme] }}</mat-icon>
              </mat-button-toggle>
            }
          </mat-button-toggle-group>
        </div>
        @if (theme.hasChoice()) {
          <button mat-menu-item (click)="reset()" test-id="theme-reset">
            <mat-icon>restart_alt</mat-icon>
            <span>{{ t('reset') }}</span>
          </button>
        }
      </mat-menu>
    </ng-container>
  `,
  styles: [
    `
      .pp-themes-button__swatch {
        border-radius: 50%;
        display: inline-block;
        height: 20px;
        margin-inline-end: 12px;
        vertical-align: middle;
        width: 20px;
      }
      .pp-themes-button__check {
        margin-inline: 12px 0;
      }
      .pp-themes-button__schemes {
        display: flex;
        justify-content: center;
        padding: 8px 12px;
      }
    `,
  ],
  imports: [MatIconButton, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, MatDivider, MatButtonToggleGroup, MatButtonToggle, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'widgets', alias: 'widgets' })],
})
export class ThemesButtonComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly presets = THEME_PRESETS;
  protected readonly schemes = THEME_COLOR_SCHEMES;
  protected readonly schemeIcons = SCHEME_ICONS;

  /** Every change the user makes, as the selection it results in. */
  readonly themeChange = output<ThemeSelection>();

  protected swatchOf(preset: ThemePreset): string {
    const [primary, tertiary] = THEME_PRESET_SWATCHES[preset];
    return `linear-gradient(135deg, ${primary} 50%, ${tertiary} 50%)`;
  }

  protected selectPreset(preset: ThemePreset): void {
    this.theme.selectPreset(preset);
    this.themeChange.emit(this.theme.selection());
  }

  protected selectScheme(change: MatButtonToggleChange): void {
    this.theme.selectScheme(change.value as ThemeColorScheme);
    this.themeChange.emit(this.theme.selection());
  }

  protected reset(): void {
    this.theme.reset();
    this.themeChange.emit(this.theme.selection());
  }
}
