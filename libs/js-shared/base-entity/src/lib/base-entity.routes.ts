import { Routes } from '@angular/router';
import { BaseEntityFormComponent } from './base-form/base-entity-form.component';
import { BaseEntityListComponent } from './base-list/base-entity-list.component';
import { BaseUrlSegments } from './base-form-navigator/base-url-segments';

export const BASE_ENTITY_ROUTES: Routes = [
  { path: '', redirectTo: 'list', pathMatch: 'full' },
  { path: ':' + BaseUrlSegments.EntityID + '/details', component: BaseEntityFormComponent },
  { path: 'list', component: BaseEntityListComponent },
];
