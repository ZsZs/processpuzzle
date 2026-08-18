import { Component, computed, inject, input, OnInit } from '@angular/core';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';

/**
 * The Preview tab's screen, mounted at `app-definition/<id>/preview` by `BASE_APP_ROUTES` — a sibling of the
 * generic Details form rather than something stacked under it, which is what the `extraTabs` hook on
 * `BaseEntityDescriptor` exists for.
 */
@Component({
  selector: 'pp-app-preview-tab',
  standalone: true,
  imports: [],
  template: `
    <div class="pp-app-preview">
      <h2 class="pp-app-preview__title">{{ title() }}</h2>
    </div>
  `,
  styles: [
    `
      .pp-app-preview {
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-app-preview__title {
        margin: 0 0 8px;
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

  protected readonly title = computed(() => (this.store.currentEntity() as AppDefinition | undefined)?.name ?? '');

  ngOnInit(): void {
    // Also sets the current entity, so the tab bar's Details link stays enabled and the status bar keeps
    // naming the record — arriving here directly, nothing else has selected it.
    this.store.setCurrentEntity(this.entityId());
  }
}
