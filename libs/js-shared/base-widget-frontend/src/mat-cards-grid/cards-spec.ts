import { ActionSpec } from './action-spec';
import { MenuItemSpec } from './menu-item-spec';

export interface CardsGridSpec {
  icon?: string;
  title: string;
  subtitle: string;
  content: Array<string>;
  actions: Array<ActionSpec>;
  menuItems?: Array<MenuItemSpec>;
  translocoPrefix: string;
}
