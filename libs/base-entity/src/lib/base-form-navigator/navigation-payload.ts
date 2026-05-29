export enum NavigatorCommand {
  SELECT_OR_CREATE = 'SelectOrCreate',
  EDIT = 'Edit',
}

export interface NavigationPayload {
  command: NavigatorCommand;
  attrName?: string;
  payload?: object;
}
