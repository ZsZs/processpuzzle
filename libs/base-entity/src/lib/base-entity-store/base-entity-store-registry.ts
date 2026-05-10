import { InjectionToken, type ProviderToken } from '@angular/core';

export type BaseEntityStoreRegistry = Record<string, ProviderToken<unknown>>;

export const BASE_ENTITY_STORE_REGISTRY = new InjectionToken<BaseEntityStoreRegistry>('BASE_ENTITY_STORE_REGISTRY', {
  factory: () => ({}),
});
