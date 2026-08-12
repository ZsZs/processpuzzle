import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BASE_ENTITY_FACADE_REGISTRY, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_DOCUMENT_ENTITY_FACADES, BASE_DOCUMENT_FACADE_PROVIDERS } from './base-document.providers';
import { Document, DocumentInputPort, DocumentOutputPort } from './domain/base-document';
import { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './domain/document-entity-names';

describe('BASE_DOCUMENT facade providers', () => {
  let injector: Injector;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ...BASE_DOCUMENT_FACADE_PROVIDERS,
        // The embedded facades reach each other's stores and descriptors through the registry, which is
        // exactly what the application wires up by spreading the same map.
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: BASE_DOCUMENT_ENTITY_FACADES },
      ],
    });
    injector = TestBed.inject(Injector);
  });

  const descriptorOf = (entityName: string): BaseEntityDescriptor => injector.get(BASE_DOCUMENT_ENTITY_FACADES[entityName]).descriptor;

  it('registers the routable document and the two embedded port lists below it', () => {
    expect(Object.keys(BASE_DOCUMENT_ENTITY_FACADES)).toEqual([DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME]);
    expect(BASE_DOCUMENT_FACADE_PROVIDERS).toHaveLength(Object.keys(BASE_DOCUMENT_ENTITY_FACADES).length);
  });

  it('keys every facade by the entity name its own descriptor declares', () => {
    Object.entries(BASE_DOCUMENT_ENTITY_FACADES).forEach(([entityName, facadeToken]) => {
      expect(injector.get(facadeToken).entityName).toBe(entityName);
    });
  });

  it('gives each entity the type its blank row is minted from', () => {
    expect(injector.get(BASE_DOCUMENT_ENTITY_FACADES[DOCUMENT_ENTITY_NAME]).entityType).toBe(Document);
    expect(injector.get(BASE_DOCUMENT_ENTITY_FACADES[DOCUMENT_INPUT_PORT_ENTITY_NAME]).entityType).toBe(DocumentInputPort);
    expect(injector.get(BASE_DOCUMENT_ENTITY_FACADES[DOCUMENT_OUTPUT_PORT_ENTITY_NAME]).entityType).toBe(DocumentOutputPort);
  });

  it('makes the document the only aggregate root of the graph', () => {
    expect(descriptorOf(DOCUMENT_ENTITY_NAME).isEmbedded).toBe(false);
    [DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME].forEach((entityName) => {
      expect(descriptorOf(entityName).isEmbedded).toBe(true);
    });
  });

  it('binds a store to every descriptor, which is what an embedded list reads its rows from', () => {
    Object.keys(BASE_DOCUMENT_ENTITY_FACADES).forEach((entityName) => expect(descriptorOf(entityName).store).toBeDefined());
  });

  /**
   * The invariant the runtime enforces by throwing on first render: `EmbeddedComponentsListComponent`
   * resolves its rows through the registry, so a child named by an `EMBEDDED_COMPONENTS` attribute but
   * missing from the map is a form that renders and then fails.
   */
  it('registers every entity an embedded attribute of the graph names', () => {
    Object.keys(BASE_DOCUMENT_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(BASE_DOCUMENT_ENTITY_FACADES[attrDescriptor.linkedEntityType as string], `'${entityName}.${attrDescriptor.attrName}' -> '${attrDescriptor.linkedEntityType}'`).toBeDefined();
        });
    });
  });

  /** The mirror of the check above: a child has to name the entity that carries it, or the control throws. */
  it('lets every embedded child name the owner that carries it', () => {
    Object.keys(BASE_DOCUMENT_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(descriptorOf(attrDescriptor.linkedEntityType as string).isComponentOf(entityName)).toBe(true);
        });
    });
  });
});
