import { EnvironmentProviders, inject, provideAppInitializer } from '@angular/core';
import { EntityRouteRegistry } from './entity-route.registry';

export function provideEntityRouteRegistry(): EnvironmentProviders {
  return provideAppInitializer(() => {
    const registry = inject(EntityRouteRegistry);
    registry.scan();
    registry.observeLazyLoads();
  });
}
