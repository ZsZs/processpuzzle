/** "Test Entity Component" → "test-entity-component" */
import { testConfig } from '../../playwright.config';

export function toRoutePath(entityName: string): string {
  return entityName.toLowerCase().replace(/\s+/g, '-');
}

export function listRoute(entityName: string): string {
  return `${testConfig.routePrefix}/${toRoutePath(entityName)}/list`;
}

export function detailRoute(entityName: string, entityId: string): string {
  return `${testConfig.routePrefix}/${toRoutePath(entityName)}/${entityId}/details`;
}
