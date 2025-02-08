import { BaseEntityContainerComponent, BaseEntityFormComponent, BaseEntityListComponent } from '@processpuzzle/base-entity';

export const TEST_FORM_ROUTES = [
  { path: '', component: BaseEntityContainerComponent },
  { path: 'list', component: BaseEntityListComponent },
  { path: ':id', component: BaseEntityFormComponent },
];
