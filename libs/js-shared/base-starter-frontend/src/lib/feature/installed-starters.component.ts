import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { INSTALLED_STARTERS_I18N_SCOPE } from '../base-starter.i18n';
import { InstalledStarter } from '../domain/starter';

/** The starters installed in the organization, with how many of their definitions were changed since. */
@Component({
  selector: 'pp-installed-starters',
  standalone: true,
  imports: [TranslocoPipe, DatePipe],
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
    .installed__customized {
      color: #ef6c00;
      font-weight: 600;
    }
  `,
})
export class InstalledStartersComponent {
  readonly starters = input.required<readonly InstalledStarter[]>();
  protected readonly scope = INSTALLED_STARTERS_I18N_SCOPE;
}
