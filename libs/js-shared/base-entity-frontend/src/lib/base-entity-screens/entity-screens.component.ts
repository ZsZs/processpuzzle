import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BaseEntityContainerComponent } from '../base-entity-container.component';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';

/** Route `data` key under which {@link entityScreenRoute} puts the descriptor to render. */
export const ENTITY_DESCRIPTOR_ROUTE_DATA_KEY = 'entityDescriptor';

/**
 * Route `data` key naming the entity the route *asked* for — which is all this component needs, and needs
 * even when nothing answered to it.
 *
 * Not `ENTITY_NAME_ROUTE_DATA_KEY`, deliberately: that key marks the route owning the entity's URL segment,
 * and everything that reads it — `readEmbeddedBreadcrumb`, `EntityRouteRegistry` — would take this route to
 * be that one. See the note in {@link entityScreenRoute}.
 */
export const REQUESTED_ENTITY_ROUTE_DATA_KEY = 'requestedEntityName';

/**
 * Hosts one entity's generated screens at a route: List and Details, with the tab bar, toolbar and status
 * bar `BaseEntityTabsComponent` brings, and the outlet its own child routes render in.
 *
 * The descriptor is **read off the route, not resolved here**. Whoever built the route resolved it — see
 * {@link entityScreenRoute} — so it is on `data` by the time this component exists. That is deliberate:
 * resolving here would mean an `await` inside the component, hence a frame with no container, and therefore
 * no `<router-outlet>`, while a child route (`…/order/list`) is already activated.
 *
 * Works for an entity of either kind, because a descriptor is a descriptor: one whose facade was compiled in
 * and one synthesized from a `BaseEntityDefinition` reach here the same way. That is what lets an
 * application mount metadata-defined entity screens with no knowledge of where the descriptor came from —
 * and with no dependency on base-app, which merely happens to be one caller.
 *
 * Without a descriptor the route stays a leaf and says so. A warning rather than a failure: whatever names
 * the entity — an `AppDefinition` route, a hand-written route in an application — is allowed to be ahead of
 * what is deployed, and the link that leads here has to render something.
 */
@Component({
  selector: 'base-entity-screens',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `
    @if (descriptor(); as resolved) {
      <base-entity-container [entityDescriptor]="resolved"></base-entity-container>
    } @else {
      <p class="base-entity-screens-unresolved">No entity type registered for '{{ entityName() }}' yet.</p>
    }
  `,
})
export class BaseEntityScreensComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly entityName = computed(() => this.route.snapshot.data[REQUESTED_ENTITY_ROUTE_DATA_KEY] as string | undefined);
  protected readonly descriptor = computed(() => this.route.snapshot.data[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY] as BaseEntityDescriptor | undefined);
}
