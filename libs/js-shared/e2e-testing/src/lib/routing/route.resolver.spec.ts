import { describe, expect, it } from 'vitest';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { entityIdFromDetailUrl, RouteResolver, toRoutePath } from './route.resolver';

const descriptorOf = (entityName: string, route?: string) => ({ entityName, route }) as BaseEntityDescriptor;

describe('toRoutePath', () => {
  it('kebab-cases a multi-word entity name', () => {
    expect(toRoutePath('Test Entity Component')).toBe('test-entity-component');
  });

  it('lower-cases a single-word name', () => {
    expect(toRoutePath('Address')).toBe('address');
  });

  /**
   * Where lower-casing alone would give `documentinputport` — not where `baseEntityRoutes` mounted it, and a
   * URL the router answers with NG04002. base-document names both its port types this way.
   */
  it('kebab-cases a camelCase name, as snakeCaseName does', () => {
    expect(toRoutePath('DocumentInputPort')).toBe('document-input-port');
    expect(toRoutePath('DocumentOutputPort')).toBe('document-output-port');
  });

  /** The acronym clause of `snakeCaseName`, transcribed with it so the two cannot drift. */
  it('breaks an acronym before the word that follows it', () => {
    expect(toRoutePath('ITVariant')).toBe('it-variant');
  });

  it('preserves the route shape when whitespace and camel-case boundaries mix', () => {
    expect(toRoutePath('HTTP Response Code')).toBe('http-response-code');
  });
});

describe('RouteResolver', () => {
  const routes = new RouteResolver('/testbed');

  it('builds a list route below the prefix', () => {
    expect(routes.listRoute(descriptorOf('Test Entity'))).toBe('/testbed/test-entity/list');
  });

  it('builds a detail route below the prefix', () => {
    expect(routes.detailRoute(descriptorOf('Test Entity'), '42')).toBe('/testbed/test-entity/42/details');
  });

  it("honours a descriptor's own route over the derived path", () => {
    expect(routes.listRoute(descriptorOf('Test Entity', '/elsewhere/te'))).toBe('/elsewhere/te/list');
  });

  it("nests an embedded row's detail route below the owner's", () => {
    const ownerUrl = '/testbed/test-entity/1/details';
    expect(routes.embeddedDetailRoute(ownerUrl, descriptorOf('Embedded Component'), 'embedded_1_1')).toBe(
      '/testbed/test-entity/1/details/embedded-component/embedded_1_1/details',
    );
  });
});

describe('entityIdFromDetailUrl', () => {
  it('reads the id back out of a detail route', () => {
    expect(entityIdFromDetailUrl('http://localhost:4200/testbed/test-entity/42/details')).toBe('42');
  });

  it("reads the row's id out of an embedded detail route, not the owner's", () => {
    expect(entityIdFromDetailUrl('http://localhost:4200/testbed/test-entity/1/details/embedded-component/embedded_1_1/details')).toBe('embedded_1_1');
  });

  it('ignores a query string, which is not part of the path', () => {
    expect(entityIdFromDetailUrl('http://localhost:4200/testbed/test-entity/42/details?mode=select')).toBe('42');
  });

  it('yields an empty id for a URL with no segment to read', () => {
    expect(entityIdFromDetailUrl('http://localhost:4200/')).toBe('');
  });
});
