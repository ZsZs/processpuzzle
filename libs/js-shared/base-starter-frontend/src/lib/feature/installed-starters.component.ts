import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { INSTALLED_STARTERS_I18N_SCOPE } from '../base-starter.i18n';
import { InstalledStarter } from '../domain/starter';

/** The starters installed in the organization, with how many of their definitions were changed since. */
@Component({
  selector: 'pp-installed-starters',
  standalone: true,
  imports: [TranslocoPipe, DatePipe, MatButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h3 class="installed__title">{{ scope + '.title' | transloco }}</h3>
    @if (starters().length === 0) {
      <p class="installed__empty" data-testid="installed-starters-empty">{{ scope + '.empty' | transloco }}</p>
    } @else {
      <table class="installed__table" data-testid="installed-starters">
        <thead>
          <tr>
            <th>{{ scope + '.column.starter' | transloco }}</th>
            <th>{{ scope + '.column.version' | transloco }}</th>
            <th>{{ scope + '.column.installedAt' | transloco }}</th>
            <th>{{ scope + '.column.installedBy' | transloco }}</th>
            <th>{{ scope + '.column.customized' | transloco }}</th>
            <th>{{ scope + '.column.upgrade' | transloco }}</th>
          </tr>
        </thead>
        <tbody>
          @for (starter of starters(); track starter.starterId) {
            <tr [attr.data-testid]="'installed-starter-' + starter.starterId">
              <td>{{ starter.starterId }}</td>
              <td>{{ starter.version }}</td>
              <td>{{ starter.installedAt | date: 'medium' }}</td>
              <td>{{ starter.installedBy ?? '—' }}</td>
              <td [class.installed__customized]="!!starter.customizedDefinitions">{{ starter.customizedDefinitions ?? 0 }}</td>
              <td>
                @if (starter.upgradeAvailable) {
                  <button
                    mat-stroked-button
                    type="button"
                    [attr.data-testid]="'upgrade-' + starter.starterId"
                    [disabled]="busy()"
                    (click)="previewUpgrade.emit(starter.starterId)"
                  >
                    {{ scope + '.upgradeTo' | transloco: { version: starter.offeredVersion } }}
                  </button>
                } @else {
                  <span class="installed__current">{{ scope + '.upToDate' | transloco }}</span>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    .installed__title {
      margin: 0 0 8px;
      font-size: 15px;
    }
    .installed__empty {
      color: #666666;
    }
    .installed__table {
      border-collapse: collapse;
      font-size: 13px;
    }
    .installed__table th,
    .installed__table td {
      text-align: left;
      padding: 4px 16px 4px 0;
      border-bottom: 1px solid #eeeeee;
    }
    .installed__current {
      color: #666666;
    }
    .installed__customized {
      color: #ef6c00;
      font-weight: 600;
    }
  `,
})
export class InstalledStartersComponent {
  readonly starters = input.required<readonly InstalledStarter[]>();
  /** Whether something is running, which disables the upgrade buttons. */
  readonly busy = input(false);
  /** The user asked to see what upgrading the starter to the version on offer would do. */
  readonly previewUpgrade = output<string>();
  protected readonly scope = INSTALLED_STARTERS_I18N_SCOPE;
}
