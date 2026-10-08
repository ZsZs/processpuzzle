import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import type { WidgetManifest, WidgetRouteConfig } from './widget-manifest.types';
import type { ActionExecutor } from './widget-event-resolver';
import {
  CustomElementWidgetController,
  type SdkContextKey,
  type WidgetHostContext,
  type WidgetStatus,
} from './custom-element-widget.controller';
import { loadScriptOnce, type ScriptLoader } from './widget-script-loader';

/** Provided by the app: executes the actions that widget events resolve to. */
export const WIDGET_ACTION_EXECUTOR = new InjectionToken<ActionExecutor>('WIDGET_ACTION_EXECUTOR');

/** Overridable, mainly for tests and for hosts that load bundles from a signed registry. */
export const WIDGET_SCRIPT_LOADER = new InjectionToken<ScriptLoader>('WIDGET_SCRIPT_LOADER', {
  providedIn: 'root',
  factory: () => loadScriptOnce,
});

export interface WidgetActionError {
  event: string;
  error: unknown;
}

/**
 * Renders one external custom-element widget.
 *
 * Usage:
 *   <pp-widget-host
 *     [manifest]="manifest"
 *     [route]="widgetRoute"
 *     [routeParams]="routeParams()"
 *     [sdkContext]="{ locale: locale(), session: sessionProxy }"
 *     [themeVariables]="{ '--pp-primary': '#0a5' }"
 *     (actionError)="onActionError($event)" />
 *
 * - A new manifest or route remounts the widget.
 * - routeParams, sdkContext and themeVariables update the mounted widget in place.
 * - `status` and `errors` are signals, so the template or a parent can react to load failures.
 */
@Component({
  selector: 'pp-widget-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #container
      class="pp-widget-host"
      [attr.data-widget-id]="manifest().metadata.id"
      [attr.data-status]="status()"
    ></div>
    @if (errors().length > 0) {
      <ul class="pp-widget-host__errors" role="alert">
        @for (message of errors(); track message) {
          <li>{{ message }}</li>
        }
      </ul>
    }
  `,
})
export class WidgetHostComponent {
  readonly manifest = input.required<WidgetManifest>();
  readonly route = input.required<WidgetRouteConfig>();
  readonly routeParams = input<Record<string, unknown>>({});
  readonly sdkContext = input<Partial<Record<SdkContextKey, unknown>>>({});
  readonly themeVariables = input<Record<string, string>>({});

  /** Emits when an action resolved from a widget event failed (permission, executor error, ...). */
  readonly actionError = output<WidgetActionError>();

  readonly status = signal<WidgetStatus>('idle');
  readonly errors = signal<string[]>([]);

  private readonly executor = inject(WIDGET_ACTION_EXECUTOR);
  private readonly loadScript = inject(WIDGET_SCRIPT_LOADER);
  private readonly container = viewChild<ElementRef<HTMLElement>>('container');
  private controller: CustomElementWidgetController | null = null;

  constructor() {
    // Remount when the manifest or route changes (or once the container exists).
    effect(() => {
      const manifest = this.manifest();
      const route = this.route();
      const container = this.container()?.nativeElement;
      if (!container) return;
      untracked(() => this.remount(manifest, route, container));
    });

    // Push context changes to the mounted widget without remounting.
    effect(() => {
      const context = this.currentContext();
      untracked(() => this.controller?.update(context));
    });

    inject(DestroyRef).onDestroy(() => this.controller?.destroy());
  }

  private currentContext(): WidgetHostContext {
    return {
      routeParams: this.routeParams(),
      sdk: this.sdkContext(),
      themeVariables: this.themeVariables(),
    };
  }

  private remount(manifest: WidgetManifest, route: WidgetRouteConfig, container: HTMLElement): void {
    this.controller?.destroy();
    this.controller = new CustomElementWidgetController({
      manifest,
      route,
      executor: this.executor,
      loadScript: this.loadScript,
      onStatus: (status, errors) => {
        this.status.set(status);
        this.errors.set(errors);
      },
      onUnhandled: (eventName) => {
        // Unbound events are ignored by design; surface them while developing.
        console.debug(`[pp-widget-host] unhandled event '${eventName}' from '${manifest.metadata.id}'`);
      },
      onActionError: (event, error) => this.actionError.emit({ event, error }),
    });
    const context = untracked(() => this.currentContext());
    void this.controller.mount(container, context);
  }
}
