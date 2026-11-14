import { computed, Directive, effect, EnvironmentInjector, inject, input, runInInjectionContext } from '@angular/core';
import { ThemeService } from '@processpuzzle/widgets';
import { themeDefaultsOf, themeVarsOf } from './app-shell.model';
import { AppIdResolver, PublishedAppStore } from './published-app-routes';

/**
 * Makes the element and everything below it wear the theme of the **published** application — the preset,
 * colour scheme and token overrides its definition declares, and the user's own choice in a `ThemesButton`,
 * remembered under the same key `AppShellComponent` uses. So the screens that work *on* the application
 * (administration, for one) look like the application instead of like the host's default.
 *
 * The standard way for a custom application's own chrome to follow the application's theme: bind it on the
 * chrome's root element, passing the {@link AppIdResolver} that finds the application. When the published
 * revision has not been read yet — a deep link straight into the administration — the directive reads it.
 *
 * `enabled` switches the theme off, for a mode that must keep the host's look (the designer).
 *
 * Like `AppShellComponent` it needs `pp-material-themes.scss` among the application's global styles.
 */
@Directive({
  selector: '[ppPublishedAppTheme]',
  providers: [ThemeService],
  host: { '[class]': 'themeClass()', '[style]': 'themeVars()' },
})
export class PublishedAppThemeDirective {
  readonly appIdResolver = input.required<AppIdResolver>({ alias: 'ppPublishedAppTheme' });
  readonly enabled = input(true, { alias: 'ppPublishedAppThemeEnabled' });

  private readonly store = inject(PublishedAppStore);
  private readonly theme = inject(ThemeService);
  private readonly injector = inject(EnvironmentInjector);
  private loading = false;

  protected readonly themeClass = computed(() => (this.enabled() ? this.theme.themeClass() : ''));
  protected readonly themeVars = computed(() => (this.enabled() ? themeVarsOf(this.store.published()) : {}));

  private readonly loadWhenUnread = effect(() => {
    if (!this.enabled() || this.store.loaded() || this.loading) return;
    this.loading = true;
    const resolver = this.appIdResolver();
    void runInInjectionContext(this.injector, resolver)
      .then((appId) => (appId ? this.store.load(appId) : this.store.clear()))
      .finally(() => (this.loading = false));
  });

  private readonly applyDefaults = effect(() => {
    const definition = this.store.published();
    this.theme.setDefaults(themeDefaultsOf(definition));
    this.theme.persistUnder(definition?.id ? `pp-theme:${definition.id}` : null);
  });
}
