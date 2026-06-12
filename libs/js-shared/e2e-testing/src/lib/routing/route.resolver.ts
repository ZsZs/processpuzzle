/** "Test Entity Component" → "test-entity-component" */
export function toRoutePath(entityName: string): string {
  return entityName.toLowerCase().replace(/\s+/g, '-');
}

export class RouteResolver {
  constructor(private readonly routePrefix: string) {}

  listRoute(entityName: string): string {
    return `${this.routePrefix}/${toRoutePath(entityName)}/list`;
  }

  detailRoute(entityName: string, entityId: string): string {
    return `${this.routePrefix}/${toRoutePath(entityName)}/${entityId}/details`;
  }
}
