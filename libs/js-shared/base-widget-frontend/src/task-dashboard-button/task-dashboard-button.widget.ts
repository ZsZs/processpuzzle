import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { TaskDashboardButtonComponent } from './task-dashboard-button.component';

export const TASK_DASHBOARD_BUTTON_WIDGET = 'task-dashboard-button';

export const TASK_DASHBOARD_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: TASK_DASHBOARD_BUTTON_WIDGET,
  component: TaskDashboardButtonComponent,
  definition: {
    name: 'Task dashboard button',
    translocoId: 'base_widget.task_dashboard_button.name',
    description: 'Leads to the workflow task dashboard, at the route the application provides, and marks itself while it is open.',
    category: 'Navigation',
    icon: 'checklist',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};
