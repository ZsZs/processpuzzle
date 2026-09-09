import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from '../../domain/definition/test-artifact-definition';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from '../../domain/definition/test-role-definition';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { WorkflowGraph } from '../../domain/modeler/workflow-graph';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';
import { RoleModelerTabComponent } from './role-modeler-tab.component';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

describe('RoleModelerTabComponent', () => {
  let fixture: ComponentFixture<RoleModelerTabComponent>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.workflow_role_definition.tabs.modeler': 'Role Modeler',
              'base_workflow.workflow_role_definition.modeler.empty': 'No roles have been defined yet.',
              'base_workflow.workflow_role_definition._self': 'Role',
              'base_workflow.artifact_definition._self': 'Artifact',
            },
          },
        }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: SERVICE_ROOT } } },
      ],
    });
    fixture = TestBed.createComponent(RoleModelerTabComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  async function render(entityId = 'clerk'): Promise<void> {
    fixture.componentRef.setInput('entityId', entityId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** `RoleDefinitionStore`'s own `onInit` hook issues this; `/roles` answers with a plain array. */
  function flushRoles(...rows: object[]): void {
    controller.expectOne(`${SERVICE_ROOT}/roles`).flush(rows);
  }

  /**
   * `ArtifactDefinitionStore`'s own `onInit` hook. Nothing else on this route branch would fetch the
   * artifact catalog — injecting the store is what asks for it, which is why this request exists at all.
   */
  function flushArtifacts(...rows: object[]): void {
    controller.expectOne(`${SERVICE_ROOT}/artifacts`).flush(rows);
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="${testid}"]`);
  }

  it('should compile and create component', async () => {
    await render();
    flushRoles(ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('draws the diagram once both catalogs have arrived', async () => {
    await render();
    flushRoles(ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    expect(query('role-modeler-diagram')).not.toBeNull();
    expect(query('role-modeler-empty')).toBeNull();
  });

  /**
   * The tab mounts per row but draws the organisation: responsibility is only legible against what everyone
   * else owns. Asserted on the graph handed to the canvas rather than on its DOM, which is ng-diagram's own
   * and is measurement-driven.
   */
  it('draws every role, with the one it was opened from marked', async () => {
    await render('manager');
    flushRoles(ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    const graph = fixture.debugElement.query(By.directive(WorkflowDiagramComponent)).componentInstance.graph as WorkflowGraph;
    expect(graph.nodes.map((node) => node.id)).toEqual(['role:clerk', 'role:manager', 'artifact:order-entity', 'artifact:fulfillment-invoice']);
    expect(graph.nodes.filter((node) => node.data.highlighted).map((node) => node.id)).toEqual(['role:manager']);
  });

  // Which symbol means what, in the words the Roles and Artifacts screens use for the two entities.
  it('explains the two symbols it draws', async () => {
    await render();
    flushRoles(ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    expect(query('modeler-legend-role')?.textContent?.trim()).toBe('Role');
    expect(query('modeler-legend-artifact')?.textContent?.trim()).toBe('Artifact');
    expect(query('modeler-legend-task')).toBeNull();
  });

  /**
   * `zoomToFit.onInit` frames the diagram when the canvas initializes, so a canvas rendered before the
   * catalogs arrive would frame nothing and stay unframed once they did.
   */
  it('renders no canvas at all until there is something to frame', async () => {
    await render();

    expect(query('role-modeler-diagram')).toBeNull();
  });

  it('says so, rather than showing an empty canvas, when no roles have been authored', async () => {
    await render();
    flushRoles();
    flushArtifacts();
    await fixture.whenStable();

    expect(query('role-modeler-diagram')).toBeNull();
    expect(text()).toContain('No roles have been defined yet.');
  });

  /**
   * Not decoration: arriving here by deep link or reload nothing else has selected the role, and with no
   * current entity the tab bar disables the Details link and the status bar stops naming the row.
   */
  it('selects the role the route addresses, so the tab bar and the status bar keep working', async () => {
    await render('manager');
    flushRoles(ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    expect(TestBed.inject(RoleDefinitionStore).currentId()).toBe('manager');
  });

  /**
   * The rows arrive after this component initializes on a deep link, which is why the selection is an
   * effect rather than a one-shot call in `ngOnInit`: `setCurrentEntity` clears the selection when it
   * resolves against an empty store, so an early call would do the opposite of what it is here for.
   */
  it('waits for the rows to arrive rather than clearing the selection before they do', async () => {
    await render();

    expect(TestBed.inject(RoleDefinitionStore).currentId()).toBeUndefined();

    flushRoles(ROLE_DEFINITION_DTO);
    flushArtifacts(ARTIFACT_DEFINITION_DTO);
    await fixture.whenStable();

    expect(TestBed.inject(RoleDefinitionStore).currentId()).toBe('clerk');
  });
});
