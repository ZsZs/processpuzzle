import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BaseEntityContainerComponent, BaseEntityDescriptorRegistry } from '@processpuzzle/base-entity';

/**
 * Renders a route whose kind is ENTITY. Resolves a BaseEntityDescriptor by entityName through
 * BaseEntityDescriptorRegistry — today that only succeeds for entity types with a compile-time
 * Facade registered (see BASE_ENTITY_FACADE_REGISTRY). A designer-created entity type with no
 * facade yet renders the "not registered" state below rather than crashing; closing that gap is
 * the separate "synthesize a descriptor from BaseEntityDefinition metadata" piece scoped alongside
 * this work, not something to fake here.
 *
 * entityMode / rsqlFilter are read but not yet wired into the descriptor — deferred for the same
 * reason `roles` is: how BaseEntityTabsComponent's list/details switch and an RSQL pre-filter are
 * meant to compose needs a look together before this guesses at it.
 */
@Component({
  selector: 'pp-route-entity',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `
    @if (descriptor(); as resolved) {
      <base-entity-container [entityDescriptor]="resolved"></base-entity-container>
    } @else {
      <p class="pp-route-entity-unresolved">No entity type registered for '{{ entityName() }}' yet.</p>
    }
  `,
})
export class RouteEntityComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly registry = inject(BaseEntityDescriptorRegistry);

  protected readonly entityName = computed(() => this.route.snapshot.data['entityName'] as string | undefined);
  protected readonly descriptor = computed(() => this.registry.getDescriptor(this.entityName()));
}
