import { ActionSpec } from './action-spec';

export interface CardsGridSpec {
  icon?: string;
  title: string;
  subtitle: string;
  content: Array<string>;
  actions: Array<ActionSpec>;
  translocoPrefix: string;
}
