import { describe, expect, it } from 'vitest';
import { ApplicationDesignerComponent } from './application-designer/application-designer.component';
import { WorkflowDesignerComponent } from './workflow-designer/workflow-designer.component';
import { WORKFLOW_DESIGNER_TABS } from './workflow-designer/workflow-designer.tabs';
import { APPLICATION_DESIGNER_TABS } from './application-designer/application-designer.tabs';
import { DESIGN_ROUTES } from './design.routes';

describe('DESIGN_ROUTES', () => {
  const applicationRoute = DESIGN_ROUTES.find((route) => route.path === 'application');
  const workflowsRoute = DESIGN_ROUTES.find((route) => route.path === 'workflows');

  it('hosts the application section on the tabbed designer page', () => {
    expect(applicationRoute?.component).toBe(ApplicationDesignerComponent);
    expect(applicationRoute?.data).toEqual({ icon: 'web', menuTitle: 'design.application' });
    expect(applicationRoute?.title).toBeDefined();
  });

  /** The tab bar carries its own scope, so the section adds nothing to what its tabs' screens resolve through. */
  it('registers no providers of its own', () => {
    expect(applicationRoute?.providers).toBeUndefined();
  });

  it('opens the first tab when the section itself is addressed', () => {
    expect(applicationRoute?.children?.[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'app-definition' });
  });

  /**
   * What lets `APPLICATION_DESIGNER_TABS` name its tabs by path without being derived from the children:
   * a tab whose route is not mounted here would render a link the router answers with NG04002.
   */
  it('mounts a child route for every tab', () => {
    const childPaths = (applicationRoute?.children ?? []).map((child) => child.path);

    expect(childPaths).toEqual(expect.arrayContaining(APPLICATION_DESIGNER_TABS.map((tab) => tab.path)));
  });

  /** The section's whole point: three entities, one page. Asserted by name, not derived, so a lost tab fails. */
  it('shows the application, its modules and the widget types they place, in that order', () => {
    expect(APPLICATION_DESIGNER_TABS.map((tab) => tab.path)).toEqual(['app-definition', 'module-definition', 'widget-definition']);
  });

  it('no longer exposes the application entities as sections of their own', () => {
    const sectionPaths = DESIGN_ROUTES.map((route) => route.path);

    expect(sectionPaths).not.toContain('app-definition');
    expect(sectionPaths).not.toContain('module-definition');
  });

  describe('the Workflows section', () => {
    it('hosts the six base-workflow branches on the tabbed designer page', () => {
      expect(workflowsRoute?.component).toBe(WorkflowDesignerComponent);
      expect(workflowsRoute?.data).toEqual({ icon: 'schema', menuTitle: 'design.workflows' });
      expect(workflowsRoute?.title).toBeDefined();
    });

    /** The tab bar carries its own scope, so the section adds nothing to what its tabs' screens resolve through. */
    it('registers no providers of its own', () => {
      expect(workflowsRoute?.providers).toBeUndefined();
    });

    it('opens the workflow branch when the section itself is addressed', () => {
      expect(workflowsRoute?.children?.[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'workflow' });
    });

    /** A tab whose route is not mounted here would render a link the router answers with NG04002. */
    it('mounts a child route for every tab', () => {
      const childPaths = (workflowsRoute?.children ?? []).map((child) => child.path);

      expect(childPaths).toEqual(expect.arrayContaining(WORKFLOW_DESIGNER_TABS.map((tab) => tab.path)));
    });

    /**
     * Every segment is `snakeCaseName(entityName)`, which `BaseFormNavigatorSingletonStore` depends on to
     * build a details URL — a renamed tab path silently breaks the Name column and Edit navigation of that
     * branch. Asserted by name, not derived, so a lost or reordered tab fails.
     */
    it('shows the workflow, the four catalog definitions and the runs, in that order', () => {
      expect(WORKFLOW_DESIGNER_TABS.map((tab) => tab.path)).toEqual([
        'workflow',
        'workflow-role-definition',
        'task-definition',
        'artifact-definition',
        'tool-definition',
        'workflow-instance',
      ]);
    });

    it('no longer exposes the workflow entities as sections of their own', () => {
      const sectionPaths = DESIGN_ROUTES.map((route) => route.path);

      expect(sectionPaths).not.toContain('workflow');
      expect(sectionPaths).not.toContain('workflow-instance');
    });
  });
});
