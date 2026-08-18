import { Component, computed, inject, input, OnInit } from '@angular/core';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { WidgetListComponent } from './widget-list.component';

/**
 * The Preview tab's screen, mounted at `app-definition/<id>/preview` by `BASE_APP_ROUTES` — a sibling of the
 * generic Details form rather than something stacked under it, which is what the `extraTabs` hook on
 * `BaseEntityDescriptor` exists for.
 */
@Component({
  selector: 'pp-app-preview-tab',
  standalone: true,
  imports: [WidgetListComponent],
  template: `
    <div class="pp-app-preview">
      @if (header(); as header) {
        <header class="pp-app-preview__header">
          <div class="pp-app-preview__brand">
            @if (app()?.logoUrl; as logoUrl) {
              <img class="pp-app-preview__logo" [src]="logoUrl" [alt]="title()" />
            }
            <h2 class="pp-app-preview__title">{{ title() }}</h2>
          </div>
          <pp-widget-list [widgets]="header.widgets ?? []" />
        </header>
      }

      <main class="pp-app-preview__content"></main>

      @if (footer(); as footer) {
        <footer class="pp-app-preview__footer">
          <pp-widget-list [widgets]="footer.widgets ?? []" />
        </footer>
      }
    </div>
  `,
  styles: [
    `
      .pp-app-preview {
        background-color: #ffffff;
        border-radius: 6px;
        display: grid;
        min-height: 320px;
        overflow: hidden;
        grid-template-rows: auto 1fr auto;
      }
      .pp-app-preview__header,
      .pp-app-preview__footer {
        align-items: center;
        background-color: var(--pp-surface-header);
        display: flex;
        gap: 16px;
        justify-content: space-between;
        padding: 8px 16px;
      }
      .pp-app-preview__brand {
        align-items: center;
        display: flex;
        gap: 12px;
        min-width: 0;
      }
      .pp-app-preview__logo {
        display: block;
        max-height: 48px;
        max-width: 160px;
        object-fit: contain;
      }
      .pp-app-preview__title {
        margin: 0;
      }
      .pp-app-preview__content {
        padding: 16px 20px 24px;
      }
    `,
  ],
})
export class AppPreviewComponent implements OnInit {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload resolve the same app definition as a
   * click through the tab does.
   */
  readonly entityId = input.required<string>();

  private readonly store = inject(AppDefinitionStore);

  protected readonly app = computed(() => this.store.currentEntity() as AppDefinition | undefined);
  protected readonly title = computed(() => this.app()?.name ?? '');
  protected readonly header = computed(() => this.app()?.regions?.find((region) => region.type === 'header'));
  protected readonly footer = computed(() => this.app()?.regions?.find((region) => region.type === 'footer'));

  ngOnInit(): void {
    // Also sets the current entity, so the tab bar's Details link stays enabled and the status bar keeps
    // naming the record — arriving here directly, nothing else has selected it.
    this.store.setCurrentEntity(this.entityId());
  }
}
