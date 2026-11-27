import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { BreadcrumbsComponent } from './breadcrumbs.component';

export const BREADCRUMBS_WIDGET = 'breadcrumbs';

export const BREADCRUMBS_WIDGET_REGISTRATION: WidgetRegistration = {
  type: BREADCRUMBS_WIDGET,
  component: BreadcrumbsComponent,
  definition: {
    name: 'Breadcrumbs',
    translocoId: 'base_widget.breadcrumbs.name',
    description: 'The path to the current page, read from the router: every level of the navigation that has a title, each a link back to it.',
    category: 'Navigation',
    icon: 'chevron_right',
    propsSchema: {
      type: 'object',
      properties: {
        showHome: {
          type: 'boolean',
          title: 'Show home',
          description: "Starts the trail with the application's root. Defaults to on.",
        },
        maxItems: {
          type: 'integer',
          title: 'Maximum items',
          description: 'A longer trail keeps its first and last items and collapses the middle into "…". 0, the default, shows all.',
        },
      },
      additionalProperties: false,
    },
  },
};
