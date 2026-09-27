import { Component, computed, inject, input } from '@angular/core';
import { APPLICATION_CONTEXT } from '../app-context/application-context';

/**
 * The application's name as a heading. Reads the name from the {@link APPLICATION_CONTEXT}; the `title` prop
 * overrides it, which is how a document — placed outside any application — names one.
 *
 * Coloured by the theme: `--mat-sys-primary` by default, `--pp-app-title-color` to override it per surface
 * (the shell's header sets it to the on-header colour, so the title stays legible on the header surface).
 * Grows to fill a row, so that in a header the widgets after it line up at the far end.
 */
@Component({
  selector: 'pp-app-title',
  standalone: true,
  template: `
    @if (text(); as text) {
      <h1 class="pp-app-title">{{ text }}</h1>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        flex: 1 1 auto;
        min-width: 0;
      }
      .pp-app-title {
        color: var(--pp-app-title-color, var(--mat-sys-primary));
        font: var(--mat-sys-headline-small);
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    `,
  ],
})
export class AppTitleComponent {
  readonly title = input<string | undefined>(undefined);

  private readonly context = inject(APPLICATION_CONTEXT, { optional: true });

  protected readonly text = computed(() => this.title() || this.context?.name() || '');
}
