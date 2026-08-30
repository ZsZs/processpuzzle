import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BASE_WORKFLOW_ROUTES } from './base-workflow.routes';

/** The branches an embedded level mounts, expanded one navigation at a time by `loadChildren`. */
async function embeddedBranchesOf(route: Route | undefined): Promise<Routes> {
  const loadChildren = route?.loadChildren;
  return loadChildren ? ((await loadChildren()) as Routes) : [];
}

/** The `:entityId/details` route of an embedded branch — the level its own children hang off. */
async function deepestDetailsOf(branch: Route | undefined): Promise<Route | undefined> {
  return (await embeddedBranchesOf(branch)).find((route) => route.path === ':entityId/details');
}

function detailsOf(route: Route): Route | undefined {
  return route.children?.find((child) => child.path === ':entityId/details');
}

describe('BASE_WORKFLOW_ROUTES', () => {
  const [workflowRoute, roleRoute, artifactRoute, taskRoute, toolRoute, instanceRoute] = BASE_WORKFLOW_ROUTES;

  // Siblings rather than a nesting, and this is the reference model itself: a role, an artifact and a
  // task are shared across workflows, so each is an aggregate with a list screen of its own.
  it('registers the six routable aggregates as siblings', () => {
    expect(BASE_WORKFLOW_ROUTES.map((route) => route.path)).toEqual([
      'workflow',
      'workflow-role-definition',
      'artifact-definition',
      'task-definition',
      'tool-definition',
      'workflow-instance',
    ]);
  });

  // `BaseFormNavigatorSingletonStore` rebuilds a details URL from the entity name, so the path has to be
  // its snake-cased form or every link it builds misses.
  it('uses the snake-cased entity name as path on each branch', () => {
    expect(workflowRoute.path).toBe('workflow');
    expect(roleRoute.path).toBe('workflow-role-definition');
    expect(artifactRoute.path).toBe('artifact-definition');
    expect(taskRoute.path).toBe('task-definition');
    expect(toolRoute.path).toBe('tool-definition');
    expect(instanceRoute.path).toBe('workflow-instance');
  });

  // `readEmbeddedBreadcrumb` opens a level on the route that *declares* the name and takes its base URL
  // from the URL so far — one route deeper and every built URL doubles the segment, silently.
  it('declares the entity name on the segment-contributing route of each branch', () => {
    expect(workflowRoute.data).toEqual({ icon: 'schema', menuTitle: 'workflow.workflows', entityName: 'Workflow' });
    expect(roleRoute.data).toEqual({ icon: 'badge', menuTitle: 'workflow.roles', entityName: 'Workflow Role Definition' });
    expect(artifactRoute.data).toEqual({ icon: 'inventory_2', menuTitle: 'workflow.artifacts', entityName: 'Artifact Definition' });
    expect(taskRoute.data).toEqual({ icon: 'assignment', menuTitle: 'workflow.tasks', entityName: 'Task Definition' });
    expect(toolRoute.data).toEqual({ icon: 'build', menuTitle: 'workflow.tools', entityName: 'Tool Definition' });
    expect(instanceRoute.data).toEqual({ icon: 'play_circle', menuTitle: 'workflow.instances', entityName: 'Workflow Instance' });
    BASE_WORKFLOW_ROUTES.forEach((route) => expect(route.title).toBeTruthy());
  });

  // base_entity is not inherited: the generic tabs resolve `base_entity.tabs.*`, and a route that declares
  // TRANSLOCO_SCOPE replaces the inherited collection. The aliases are asserted too, because left to
  // transloco's default they would be camel-cased (`baseWorkflow`) and no key would resolve.
  it.each(BASE_WORKFLOW_ROUTES.map((route, index) => [route.path, index] as [string, number]))('binds %s to its facade and registers both transloco scopes', (_path, index) => {
    const providers = (BASE_WORKFLOW_ROUTES[index].providers?.flat() ?? []) as Array<{ useValue?: unknown; provide?: unknown }>;

    expect(providers[0].provide).toBeDefined();
    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_workflow', alias: 'base_workflow' },
    ]);
  });

  it('gives each branch the generic list and details routes and no extra tab', () => {
    BASE_WORKFLOW_ROUTES.forEach((route) => expect(route.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']));
  });

  describe('the workflow branch', () => {
    // The roles, artifacts and tools a workflow involves are references now, picked from their own
    // branches — so there is no URL under a workflow that addresses them, and the assignments are the
    // only child left.
    // Five embedded levels, not one: besides the task assignments, the three `*Use` rows through which a
    // workflow involves a role, an artifact or a tool, and the required artifacts of its start
    // condition. A `*Use` is a URL of its own because the contract makes it an object rather than an id -
    // the definition it names is still edited on its own branch.
    it('hangs the assignments, the three Use rows and the required artifacts below the workflow', async () => {
      const branches = await embeddedBranchesOf(detailsOf(workflowRoute));

      expect(branches.map((branch) => branch.path)).toEqual([
        'workflow-task-assignment',
        'workflow-role-use',
        'workflow-artifact-use',
        'workflow-tool-use',
        'workflow-required-start-artifact',
      ]);
      expect(branches.map((branch) => branch.data?.['entityName'])).toEqual([
        'Workflow Task Assignment',
        'Workflow Role Use',
        'Workflow Artifact Use',
        'Workflow Tool Use',
        'Workflow Required Start Artifact',
      ]);
      branches.forEach((branch) => expect(branch.data?.['embeddedEntity']).toBe(true));
    });

    it('stops at each of them, none nesting anything further', async () => {
      const branches = await embeddedBranchesOf(detailsOf(workflowRoute));

      for (const branch of branches) {
        expect(await embeddedBranchesOf(await deepestDetailsOf(branch))).toEqual([]);
      }
    });
  });

  describe('the catalog branches', () => {
    // A role and an artifact nest nothing in this contract, so each is a plain list-and-details branch.
    it('give the role and the artifact no embedded children', async () => {
      expect(await embeddedBranchesOf(detailsOf(roleRoute))).toEqual([]);
      expect(await embeddedBranchesOf(detailsOf(artifactRoute))).toEqual([]);
    });

    // The three rows that moved with the task when it left the workflow: none has an endpoint of its own,
    // so each is addressed through the task that carries it.
    // Only the steps. A task's `inputs` and `outputs` are artifact definition ids by contract, picked on
    // the task's own form and edited under `artifact-definition`, so no URL below a task addresses them.
    it('hang only the steps below the task being edited', async () => {
      const branches = await embeddedBranchesOf(detailsOf(taskRoute));

      expect(branches.map((branch) => branch.path)).toEqual(['task-step-definition']);
      expect(branches[0].data?.['entityName']).toBe('Task Step Definition');
      expect(branches[0].data?.['embeddedEntity']).toBe(true);
    });

    it('hangs the operations below the tool being edited', async () => {
      const branches = await embeddedBranchesOf(detailsOf(toolRoute));

      expect(branches.map((branch) => branch.path)).toEqual(['tool-operation']);
      expect(branches[0].data?.['entityName']).toBe('Tool Operation');
    });
  });

  describe('the instance branch', () => {
    it('hangs the tasks and the artifacts below the run', async () => {
      const branches = await embeddedBranchesOf(detailsOf(instanceRoute));

      expect(branches.map((branch) => branch.path)).toEqual(['task-instance', 'artifact-instance']);
    });

    it('hangs the step results one level below a task instance', async () => {
      const [taskBranch] = await embeddedBranchesOf(detailsOf(instanceRoute));

      const branches = await embeddedBranchesOf(await deepestDetailsOf(taskBranch));

      expect(branches.map((branch) => branch.path)).toEqual(['task-step-result']);
    });
  });
});
