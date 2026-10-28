import { ActionSpec } from './action-spec';
import { MenuItemSpec } from './menu-item-spec';

export interface CardsGridSpec {
  icon?: string;
  /** Where the whole card leads, as a router link; the action buttons keep their own targets. */
  link?: string;
  /** A URL or a `module:` asset drawn behind the card, bottom-left; the text then keeps to the right. */
  backgroundImage?: string;
  title: string;
  subtitle: string;
  content: Array<string>;
  actions: Array<ActionSpec>;
  menuItems?: Array<MenuItemSpec>;
  translocoPrefix: string;
}
