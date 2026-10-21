import { APIRequestContext, expect } from '@playwright/test';
import { ServiceRoots } from './race-sample.api';

/**
 * REST access to the platform features around an entity — the state machine, the release, workflow instances
 * and calculation runs. Specs use it to set a scenario up and to check what the backend *did*, which is the only
 * way to observe a platform event: nothing lists published events, so a fact is asserted through its effect —
 * a started instance, a calculation run, a state.
 *
 * The entities themselves are {@link EntityApi}'s business.
 */

export interface TransitionResult {
  success: boolean;
  [key: string]: unknown;
}

export interface WorkflowInstanceSummary {
  id: string;
  workflowId: string;
  status: string;
  [key: string]: unknown;
}

export interface CalculationRunSummary {
  id: string;
  calculationId: string;
  subjectId: string;
  status: string;
  frozen?: boolean;
  triggeredBy?: string;
  [key: string]: unknown;
}

export class PlatformApi {
  constructor(
    private readonly request: APIRequestContext,
    private readonly roots: ServiceRoots,
  ) {}

  /** Fires the named trigger on an entity object; `version` is the object's current one. */
  async fireTransition(entityName: string, objectId: string, triggerKey: string, version: number): Promise<TransitionResult> {
    const response = await this.request.post(`${this.roots.backend}/entities/${entityName}/${encodeURIComponent(objectId)}/state-transitions`, {
      data: { triggerKey, version },
    });
    expect(response.status(), `firing ${triggerKey} on ${entityName}/${objectId}: ${await response.text()}`).toBe(200);
    return (await response.json()) as TransitionResult;
  }

  /**
   * Releases every pending definition of the organization. Event definitions, workflows and calculations
   * only take effect once released, so a spec that creates one through the UI releases before it expects
   * the platform to react. A 409 means nothing was pending, which is as good as released.
   */
  async release(): Promise<void> {
    const response = await this.request.post(`${this.roots.backend}/releases`, { data: {} });
    expect([201, 409], `releasing: ${response.status()} ${await response.text()}`).toContain(response.status());
  }

  async workflowInstances(query: { workflowId?: string; status?: string; entityId?: string } = {}): Promise<WorkflowInstanceSummary[]> {
    const response = await this.request.get(`${this.roots.backend}/instances`, { params: { ...query, size: 200 } });
    expect(response.ok(), `listing workflow instances: ${response.status()}`).toBe(true);
    return (await response.json()).content ?? [];
  }

  async calculationRuns(calculationId: string, subjectId?: string): Promise<CalculationRunSummary[]> {
    const params: Record<string, string> = { calculationId };
    if (subjectId) params['subjectId'] = subjectId;
    const response = await this.request.get(`${this.roots.backend}/calculation-runs`, { params });
    expect(response.ok(), `listing calculation runs: ${response.status()}`).toBe(true);
    const body = await response.json();
    return Array.isArray(body) ? body : (body.content ?? []);
  }

  /** Creates a calculation definition from its YAML source; its `calculation` key is the id. Remove it with {@link deleteCalculation}. */
  async createCalculation(source: string): Promise<string> {
    const response = await this.request.post(`${this.roots.backend}/calculation-definitions`, { data: { source } });
    expect(response.status(), `creating a calculation: ${await response.text()}`).toBe(201);
    return (await response.json()).id as string;
  }

  async calculationSource(id: string): Promise<string> {
    const response = await this.request.get(`${this.roots.backend}/calculation-definitions/${encodeURIComponent(id)}`);
    expect(response.ok(), `reading calculation ${id}: ${response.status()}`).toBe(true);
    return (await response.json()).source as string;
  }

  async deleteCalculation(id: string): Promise<void> {
    const response = await this.request.delete(`${this.roots.backend}/calculation-definitions/${encodeURIComponent(id)}`);
    expect([204, 404], `deleting calculation ${id}`).toContain(response.status());
  }

  /** Creates an empty report template with one required `ENTITY_REF` input port; remove it with {@link deleteDocument}. */
  async createReportTemplate(slug: string, title: string, port: { name: string; entityType: string }): Promise<string> {
    const response = await this.request.post(`${this.roots.backend}/documents`, {
      data: { slug, title, sourceLocale: 'en', kind: 'REPORT_TEMPLATE', inputPorts: [{ ...port, type: 'ENTITY_REF', required: true }], outputPorts: [] },
    });
    expect(response.status(), `creating report template ${slug}: ${await response.text()}`).toBe(201);
    return (await response.json()).id as string;
  }

  async deleteDocument(id: string): Promise<void> {
    const response = await this.request.delete(`${this.roots.backend}/documents/${encodeURIComponent(id)}`);
    expect([204, 404], `deleting document ${id}`).toContain(response.status());
  }

  /** Imports a SPEM YAML catalog (roles, tasks, workflows) in one all-or-nothing call. */
  async importWorkflows(yaml: string): Promise<void> {
    const response = await this.request.post(`${this.roots.backend}/workflows/import`, {
      multipart: { file: { name: 'workflows.yaml', mimeType: 'application/x-yaml', buffer: Buffer.from(yaml) } },
    });
    expect(response.status(), `importing workflows: ${await response.text()}`).toBe(200);
    expect((await response.json()).errors, 'import errors').toEqual([]);
  }

  /** The saved modeler arrangement of a workflow, or `undefined` while it has never been arranged. */
  async workflowLayout(workflowId: string): Promise<Record<string, unknown> | undefined> {
    const response = await this.request.get(`${this.roots.backend}/workflow-diagrams/${encodeURIComponent(workflowId)}`);
    if (response.status() === 404) return undefined;
    expect(response.ok(), `reading the layout of ${workflowId}: ${response.status()}`).toBe(true);
    return (await response.json()) as Record<string, unknown>;
  }

  /** Removes definitions by collection and id, in the order given; absent ones are fine. */
  async deleteDefinitions(definitions: { collection: 'workflows' | 'tasks' | 'roles'; id: string }[]): Promise<void> {
    for (const { collection, id } of definitions) {
      const response = await this.request.delete(`${this.roots.backend}/${collection}/${encodeURIComponent(id)}`);
      expect([204, 404], `deleting ${collection}/${id}`).toContain(response.status());
    }
  }
}
