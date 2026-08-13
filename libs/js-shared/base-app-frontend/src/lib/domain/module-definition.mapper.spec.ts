import { describe, expect, it } from 'vitest';
import { RouteDefinition } from './app-definition';
import { ModuleDefinition } from './module-definition';
import { ModuleDefinitionMapper } from './module-definition.mapper';

const MODULE_DTO = {
  key: 'order-admin',
  name: 'Order administration',
  translocoId: 'order_admin.module.name',
  description: 'Back-office order line screens.',
  translocoScope: 'order_admin',
  routes: [
    { path: 'lines', title: 'All order lines', icon: 'view_list', target: { kind: 'ENTITY', entityName: 'Order Line', entityMode: 'LIST' } },
    { path: 'line/:id', title: 'Order line', target: { kind: 'ENTITY', entityName: 'Order Line', entityMode: 'DETAILS' } },
  ],
  orgKey: 'processpuzzle-testbed',
  version: 2,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

describe('ModuleDefinitionMapper', () => {
  const mapper = new ModuleDefinitionMapper();

  it("reads the contract's key as the id base-entity addresses the module by", () => {
    const module = mapper.fromDto(MODULE_DTO);

    expect(module.id).toBe('order-admin');
    expect(module).not.toHaveProperty('key');
  });

  it('reads the rest of the module verbatim', () => {
    const module = mapper.fromDto(MODULE_DTO);

    expect(module.name).toBe('Order administration');
    expect(module.translocoId).toBe('order_admin.module.name');
    expect(module.description).toBe('Back-office order line screens.');
    expect(module.translocoScope).toBe('order_admin');
    expect(module.orgKey).toBe('processpuzzle-testbed');
    expect(module.version).toBe(2);
    expect(module.createdAt).toBe('2026-01-01T00:00:00Z');
    expect(module.updatedAt).toBe('2026-02-01T00:00:00Z');
  });

  // The same flattening AppDefinitionMapper performs, so a route authored in a module edits like one
  // authored in an app: the generated form addresses one property, not a path into `target`.
  it('flattens each route target onto the row the form edits', () => {
    const routes = mapper.fromDto(MODULE_DTO).routes;

    expect(routes?.[0].kind).toBe('ENTITY');
    expect(routes?.[0].entityName).toBe('Order Line');
    expect(routes?.[0].entityMode).toBe('LIST');
    expect(routes?.[0].icon).toBe('view_list');
    expect(routes?.[1].path).toBe('line/:id');
    expect(routes?.[1].entityMode).toBe('DETAILS');
  });

  it('answers a module the backend has not filled in yet with empty required fields', () => {
    const module = mapper.fromDto({});

    expect(module.id).toBe('');
    expect(module.name).toBe('');
    expect(module.routes).toBeUndefined();
  });

  it('writes the id back as key, so the id it became does not travel beside it', () => {
    const dto = mapper.toDto(new ModuleDefinition({ id: 'order-admin', name: 'Order administration' }));

    expect(dto.key).toBe('order-admin');
    expect(dto).not.toHaveProperty('id');
  });

  it('re-nests each route target on the way out', () => {
    const module = new ModuleDefinition({
      id: 'order-admin',
      name: 'Order administration',
      routes: [new RouteDefinition({ path: 'lines', title: 'All order lines', kind: 'ENTITY', entityName: 'Order Line', entityMode: 'LIST' })],
    });

    const dto = mapper.toDto(module);

    expect(dto.routes).toEqual([{ path: 'lines', title: 'All order lines', translocoId: undefined, icon: undefined, roles: undefined, target: { kind: 'ENTITY', widgets: undefined, documentSlug: undefined, entityName: 'Order Line', entityMode: 'LIST', rsqlFilter: undefined } }]);
  });

  it('round-trips a module without losing a field', () => {
    expect(mapper.toDto(mapper.fromDto(MODULE_DTO))).toEqual(MODULE_DTO);
  });
});
