import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Fallback rendered by {@link AppRouteRenderer} for a route kind it doesn't yet know how to build a
 * real component for (today: DOCUMENT, and any kind value the frontend doesn't recognize). Exists so
 * an unfinished part of the shell fails visibly and specifically at the one route that hits it,
 * rather than throwing during route registration and taking the whole app down, or — worse — silently
 * rendering nothing and leaving a designer to wonder why their route is blank.
 *
 * Not an error state: an app definition is allowed to reference a route kind the frontend hasn't
 * caught up to yet, same spirit as a NavItem naming a route that hasn't been authored being a warning
 * rather than a validation failure elsewhere in this library.
 */
@Component({
  selector: 'pp-route-unsupported',
  standalone: true,
  template: `<p class="pp-route-unsupported">{{ reason() }}</p>`,
})
export class RouteUnsupportedComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly reason = computed(() => (this.route.snapshot.data['reason'] as string) ?? 'This route is not yet supported.');
}
