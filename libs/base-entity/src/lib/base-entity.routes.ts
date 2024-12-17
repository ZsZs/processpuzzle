import { Routes } from '@angular/router';
import { BaseEntityFormComponent } from './base-form/base-entity-form.component';
import { BaseEntityListComponent } from './base-list/base-entity-list.component';

export const BASE_ENTITY_ROUTES: Routes = [
  { path: '', redirectTo: 'list', pathMatch: 'full' },
  { path: ':id/details', component: BaseEntityFormComponent },
  { path: 'list', component: BaseEntityListComponent },
];
