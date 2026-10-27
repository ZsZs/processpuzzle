/*
 * Public API Surface of @processpuzzle/design
 */

export { ApplicationDesignerComponent } from './lib/application-designer/application-designer.component';
export { APPLICATION_DESIGNER_TABS } from './lib/application-designer/application-designer.tabs';
export type { ApplicationDesignerTab } from './lib/application-designer/application-designer.tabs';
export { CalculationDesignerComponent } from './lib/calculation-designer/calculation-designer.component';
export { CALCULATION_DESIGNER_TABS } from './lib/calculation-designer/calculation-designer.tabs';
export type { CalculationDesignerTab } from './lib/calculation-designer/calculation-designer.tabs';
export { DesignContentComponent } from './lib/content/design-content.component';
export { DESIGN_LIBRARY } from './lib/design';
export { DESIGN_ROUTES, DESIGN_SECTIONS } from './lib/design.routes';
export { DesignRouteService } from './lib/design-route/design-route.service';
export { DesignSidenavComponent } from './lib/design-sidenav/design-sidenav.component';
export { UnderConstructionComponent } from './lib/under-construction/under-construction.component';
export { DesignWorkbenchComponent } from './lib/workbench/design-workbench.component';
export { DESIGN_ASSISTANT_ENABLED, provideDesignAssistant } from './lib/workbench/design-assistant.provider';
