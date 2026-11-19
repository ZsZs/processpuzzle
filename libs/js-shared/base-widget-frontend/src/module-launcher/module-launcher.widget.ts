import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { ModuleLauncherComponent } from './module-launcher.component';

export const MODULE_LAUNCHER_WIDGET = 'module-launcher';

// No props: what it shows comes from the application it is placed in — the modules mounted into it, with the cards
// their business starters described — through the shell's MODULE_LAUNCHER_CONTEXT.
export const MODULE_LAUNCHER_WIDGET_REGISTRATION: WidgetRegistration = {
  type: MODULE_LAUNCHER_WIDGET,
  component: ModuleLauncherComponent,
  definition: {
    name: 'Module launcher',
    translocoId: 'base_widget.module_launcher.name',
    description: 'A card for every module mounted into the application that brought one, such as the modules of an installed business starter.',
    category: 'Navigation',
    icon: 'apps',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};
