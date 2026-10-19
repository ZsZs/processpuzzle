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
}
