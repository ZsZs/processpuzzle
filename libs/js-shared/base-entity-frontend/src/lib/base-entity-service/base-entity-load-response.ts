import { BaseEntity } from '../base-entity/base-entity';
import { DocumentSnapshot, WhereFilterOp } from '@angular/fire/firestore';

export interface WhereCondition<Entity extends BaseEntity> {
  field: keyof Entity;
  operator: WhereFilterOp;
  value: Entity[keyof Entity];
}

export interface OrderByCondition<Entity extends BaseEntity> {
  field: keyof Entity;
  order?: OrderByDirection;
}

export interface FilterCondition {
  property: string;
  operator: WhereFilterOp;
  value: string;
}

export type LogicalOperator = 'AND' | 'OR';

export interface FilterGroup {
  operator: LogicalOperator;
  conditions: Array<FilterCondition | FilterGroup>;
}

export function isFilterGroup(node: FilterCondition | FilterGroup): node is FilterGroup {
  return (node as FilterGroup).conditions !== undefined;
}

export enum OrderByDirection {
  ASC = 'asc',
  DESC = 'desc',
}

export interface OrderBy {
  property: string;
  direction: string;
}

export interface BaseEntityQueryCondition {
  path?: string;
  hash?: string;
  page?: number;
  pageSize?: number;
  pathParams?: Map<string, string>;
  filters?: FilterCondition[];
  filterGroup?: FilterGroup;
  orderBys?: OrderBy[];
  query?: string;
}

export interface FirestoreQuery<Entity extends BaseEntity> {
  limit?: number;
  startAt?: number | string | DocumentSnapshot<Entity>;
  startAfter?: number | string | DocumentSnapshot<Entity> | null;
  endAt?: number | DocumentSnapshot<Entity>;
  endBefore?: number | DocumentSnapshot<Entity>;
  orderBy?: OrderByCondition<Entity>[];
  where?: WhereCondition<Entity>[];
}

export interface BaseEntityLoadResponse<Entity extends BaseEntity> {
  page: number | undefined;
  pageSize: number | undefined;
  totalPageCount: number | undefined;
  content: Entity[];
}
