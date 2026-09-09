import { describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus, RouteDefinition } from './app-definition';
import { AppDefinitionMapper } from './app-definition.mapper';
import { APP_DEFINITION_DTO } from './test-app-definition';

describe('AppDefinitionMapper', () => {
  const mapper = new AppDefinitionMapper();

  const dto = APP_DEFINITION_DTO;

  describe('fromDto', () => {
    it('flattens the theme onto the entity', () => {
      const entity = mapper.fromDto(dto);

      expect(entity.materialTheme).toBe('azure-blue');
      expect(entity.colorScheme).toBe('light');
      expect(entity.tokenOverrides).toEqual({ '--pp-surface-sidenav': '#0d1b2a' });
      expect(entity.logoUrl).toBe('logo.png');
      expect(entity.faviconUrl).toBeUndefined();
    });

    it('flattens the layout onto the entity', () => {
      const entity = mapper.fromDto(dto);

      expect(entity.preset).toBe('sidenav-left');
      expect(entity.sidenavMode).toBe('side');
      expect(entity.sidenavCollapsible).toBe(true);
      expect(entity.sidenavOpenByDefault).toBe(false);
      expect(entity.contentMaxWidth).toBe('1280px');
    });

    it('keeps the definition graph and the server-assigned fields', () => {
      const entity = mapper.fromDto(dto);

      expect(entity.regions).toEqual(dto.regions);
      expect(entity.modules).toEqual(dto.modules);
      expect(entity.orgKey).toBe('processpuzzle-testbed');
      expect(entity.status).toBe(AppDefinitionStatus.DRAFT);
      expect(entity.version).toBe(3);
      expect(entity.publishedVersion).toBe(2);
    });

    // The generic form addresses one property per control and knows nothing of a discriminated union,
    // so the target is flattened the same way theme and layout are.
    it('flattens the route target onto each route', () => {
      const route = mapper.fromDto(dto).routes?.[0];

      expect(route?.path).toBe('orders');
      expect(route?.title).toBe('Orders');
      expect(route?.kind).toBe('WIDGETS');
      expect(route?.widgets).toEqual([{ id: 'order-grid', type: 'entity-grid', props: { entityName: 'Order' } }]);
    });

    it('flattens the fields of a DOCUMENT and of an ENTITY target as well', () => {
      const entity = mapper.fromDto({
        id: 'demo',
        name: 'Demo',
        routes: [
          { path: 'handbook', title: 'Handbook', target: { kind: 'DOCUMENT', documentSlug: 'employee-handbook' } },
          { path: 'orders', title: 'Orders', target: { kind: 'ENTITY', entityName: 'Order', entityMode: 'LIST', rsqlFilter: 'status==OPEN' } },
        ],
      });

      expect(entity.routes?.[0].documentSlug).toBe('employee-handbook');
      expect(entity.routes?.[1].entityName).toBe('Order');
      expect(entity.routes?.[1].entityMode).toBe('LIST');
      expect(entity.routes?.[1].rsqlFilter).toBe('status==OPEN');
    });

    it('tolerates a definition without theme, layout, routes and modules', () => {
      const entity = mapper.fromDto({ id: 'bare', name: 'Bare' });

      expect(entity.materialTheme).toBeUndefined();
      expect(entity.preset).toBeUndefined();
      expect(entity.theme).toBeUndefined();
      expect(entity.layout).toBeUndefined();
      expect(entity.routes).toBeUndefined();
      expect(entity.modules).toBeUndefined();
    });

    it('leaves a route without a target with no kind rather than inventing one', () => {
      const entity = mapper.fromDto({ id: 'bare', name: 'Bare', routes: [{ path: 'orders', title: 'Orders' }] });

      expect(entity.routes?.[0].kind).toBeUndefined();
      expect(entity.routes?.[0].target).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('rebuilds theme and layout from the flattened controls', () => {
      const entity = mapper.fromDto(dto);
      entity.colorScheme = 'dark';
      entity.sidenavMode = 'over';

      const result = mapper.toDto(entity);

      expect(result.theme).toEqual({ materialTheme: 'azure-blue', colorScheme: 'dark', tokenOverrides: { '--pp-surface-sidenav': '#0d1b2a' }, logoUrl: 'logo.png' });
      expect(result.layout).toEqual({ preset: 'sidenav-left', sidenavMode: 'over', sidenavCollapsible: true, sidenavOpenByDefault: false, contentMaxWidth: '1280px' });
    });

    it('rebuilds the branding and the token map the theme controls edit', () => {
      const entity = mapper.fromDto(dto);
      entity.tokenOverrides = { '--pp-surface-header': '#a8e6cf' };
      entity.faviconUrl = 'favicon.ico';

      const result = mapper.toDto(entity);

      expect(result.theme.tokenOverrides).toEqual({ '--pp-surface-header': '#a8e6cf' });
      expect(result.theme.faviconUrl).toBe('favicon.ico');
      expect(result.theme.logoUrl).toBe('logo.png');
    });

    it('keeps the parts of the graph the form never shows', () => {
      const result = mapper.toDto(mapper.fromDto(dto));

      expect(result.regions).toEqual(dto.regions);
      expect(result.modules).toEqual(dto.modules);
    });

    it('re-nests the target of every route', () => {
      const entity = mapper.fromDto(dto);
      const route = entity.routes?.[0] as RouteDefinition;
      route.kind = 'ENTITY';
      route.entityName = 'Order';

      const result = mapper.toDto(entity);

      expect(result.routes?.[0].target?.kind).toBe('ENTITY');
      expect(result.routes?.[0].target?.entityName).toBe('Order');
      expect(result.routes?.[0]).not.toHaveProperty('kind');
      expect(result.routes?.[0]).not.toHaveProperty('entityName');
    });

    // A DOCUMENT or ENTITY route would otherwise persist an empty array it never renders.
    it('omits the widgets of a target that holds none', () => {
      const entity = mapper.fromDto({ id: 'demo', name: 'Demo', routes: [{ path: 'handbook', title: 'Handbook', target: { kind: 'DOCUMENT', documentSlug: 'handbook', widgets: [] } }] });

      const result = mapper.toDto(entity);

      expect(result.routes?.[0].target?.widgets).toBeUndefined();
    });

    it('does not leak the flattened controls into the payload', () => {
      const result = mapper.toDto(mapper.fromDto(dto));

      expect(result).not.toHaveProperty('materialTheme');
      expect(result).not.toHaveProperty('colorScheme');
      expect(result).not.toHaveProperty('preset');
      expect(result).not.toHaveProperty('sidenavMode');
      expect(result).not.toHaveProperty('sidenavCollapsible');
      expect(result).not.toHaveProperty('sidenavOpenByDefault');
      expect(result).not.toHaveProperty('contentMaxWidth');
    });

    it('round-trips a definition unchanged when nothing is edited', () => {
      expect(mapper.toDto(mapper.fromDto(dto))).toEqual(dto);
    });

    it('accepts the plain object the form submits, not only an AppDefinition instance', () => {
      const edited = { ...mapper.fromDto(dto), name: 'Renamed', materialTheme: 'cyan-orange' } as AppDefinition;

      const result = mapper.toDto(edited);

      expect(result.name).toBe('Renamed');
      expect(result.theme.materialTheme).toBe('cyan-orange');
      expect(result.routes).toEqual(dto.routes);
      expect(result.modules).toEqual(dto.modules);
    });
  });
});
