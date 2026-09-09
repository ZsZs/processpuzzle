import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { RouteDefinition } from '../domain/app-definition';
import { createRouteDefinitionDescriptor } from '../domain/route-definition.descriptors';

/**
 * `GET .../app-definitions/{appId}/layout` is a read-only projection for the run-time shell, not a write
 * endpoint — a route is authored inside the `App Definition` document, so its store goes through that
 * document like every other embedded child of the graph.
 */
@Injectable()
export class AppRouteFacade extends EmbeddedEntityFacade<RouteDefinition> {
  readonly entityType = RouteDefinition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRouteDefinitionDescriptor();
  }
}
