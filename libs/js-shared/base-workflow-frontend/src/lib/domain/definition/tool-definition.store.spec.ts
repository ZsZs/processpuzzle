import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ToolDefinitionStore } from './tool-definition.store';
import { OTHER_TOOL_DEFINITION_DTO, pageOfToolDefinitions, TOOL_DEFINITION_DTO } from './test-tool-definition';

describe('ToolDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof ToolDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(ToolDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/tools`).flush(pageOfToolDefinitions(TOOL_DEFINITION_DTO, OTHER_TOOL_DEFINITION_DTO));
  });

  it('loads the tools of the organization on init', () => {
    expect(store.entities().map((entity) => entity.id)).toEqual(['automated-check-tool', 'public-rates-tool']);
  });

  it('exposes the tool with its auth flattened and its operations intact', () => {
    store.setCurrentEntity('automated-check-tool');

    expect(store.currentEntity()?.type).toBe('BEARER_TOKEN');
    expect(store.currentEntity()?.secretRef).toBe('AUTOMATED_CHECK_TOKEN');
    expect(store.currentEntity()?.operations).toHaveLength(2);
  });
});
