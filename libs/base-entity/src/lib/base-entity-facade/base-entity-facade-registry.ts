import { Component, inject, InjectionToken, OnInit, type ProviderToken } from '@angular/core';
import { BaseEntityFacade } from './base-entity-facade';

export type BaseEntityFacadeRegistry = Record<string, ProviderToken<BaseEntityFacade<any>>>;

export const BASE_ENTITY_FACADE_REGISTRY = new InjectionToken<BaseEntityFacadeRegistry>('BASE_ENTITY_FACADE_REGISTRY', {
  factory: () => ({}),
});

@Component({
  selector: 'pp-entity-registry',
  template: '',
})
export class EntityRegistryComponent implements OnInit {
  private registry = inject(BASE_ENTITY_FACADE_REGISTRY);

  ngOnInit() {
    // expose on window for Playwright to read
  }
}
