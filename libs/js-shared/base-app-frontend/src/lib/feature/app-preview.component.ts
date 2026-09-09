import { Component, computed, inject, input, OnInit } from '@angular/core';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppShellComponent } from './shell/app-shell.component';

/**
 * The Preview tab's screen, mounted at `app-definition/<id>/preview` by `BASE_APP_ROUTES` — a sibling of
 * the generic Details form rather than something stacked under it, which is what the `extraTabs` hook on
 * `BaseEntityDescriptor` exists for.
 *
 * A **container and nothing more**: it resolves which definition is being previewed and frames the
 * result. Everything about *how* an application renders belongs to {@link AppShellComponent}, which the
 * standalone runtime host will mount just as this does — so the preview cannot grow behaviour the real
 * shell lacks, which is the only way a preview stays honest.
 *
 * The frame below — rounded, white, and tall enough to look like an application — is the preview's own,
 * not the shell's. In production the shell fills the viewport and none of it applies.
 */
@Component({
  selector: 'pp-app-preview-tab',
  standalone: true,
  imports: [AppShellComponent],
  template: `
    <div class="pp-app-preview">
      <pp-app-shell [definition]="app()" />
    </div>
  `,
  styles: [
    `
      .pp-app-preview {
        background-color: var(--pp-surface-base, #ffffff);
        border-radius: 6px;
        overflow: hidden;
      }
      /*
       * How tall a previewed application is, and therefore where its footer sits: at the bottom of this
       * box, not directly under the header. The shell is a three-row grid whose middle row takes the slack,
       * so giving it a height is all that is needed — but something has to, and nothing above this can:
       * the tab panel, the router outlets and the designer's own layout are all auto-height, so a
       * percentage here would resolve against nothing.
       *
       * Viewport-relative rather than a calc() subtracting the chrome: what sits above a preview is a tab
       * bar, a status bar and a toolbar whose heights are Material's to change, and a subtraction would go
       * quietly wrong when they do.
       */
      .pp-app-preview > pp-app-shell {
        min-height: 65vh;
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

  ngOnInit(): void {
    // Also sets the current entity, so the tab bar's Details link stays enabled and the status bar keeps
    // naming the record — arriving here directly, nothing else has selected it.
    this.store.setCurrentEntity(this.entityId());
  }
}
