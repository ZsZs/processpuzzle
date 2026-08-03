import { describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus } from './app-definition';
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
      expect(entity.pages).toEqual(dto.pages);
      expect(entity.orgKey).toBe('processpuzzle-testbed');
      expect(entity.status).toBe(AppDefinitionStatus.DRAFT);
      expect(entity.version).toBe(3);
      expect(entity.publishedVersion).toBe(2);
    });

    it('tolerates a definition without theme and layout', () => {
      const entity = mapper.fromDto({ id: 'bare', name: 'Bare' });

      expect(entity.materialTheme).toBeUndefined();
      expect(entity.preset).toBeUndefined();
      expect(entity.theme).toBeUndefined();
      expect(entity.layout).toBeUndefined();
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
      expect(result.pages).toEqual(dto.pages);
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
      expect(result.pages).toEqual(dto.pages);
    });
  });
});
