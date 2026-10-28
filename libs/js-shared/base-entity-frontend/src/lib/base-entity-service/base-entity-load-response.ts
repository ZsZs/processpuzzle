import { BaseEntity } from '../base-entity/base-entity';

/** The comparison operators a filter condition may use; `toRsql` maps each onto its RSQL/FIQL operator. */
export type FilterOperator = '<' | '<=' | '==' | '!=' | '>=' | '>' | 'array-contains' | 'in' | 'array-contains-any' | 'not-in';

export interface OrderByCondition<Entity extends BaseEntity> {
  field: keyof Entity;
  order?: OrderByDirection;
}

export interface FilterCondition {
  property: string;
  operator: FilterOperator;
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
  /**
   * Read every page and answer them as one, for a screen that works on the whole set (a dashboard) rather than on a
   * page of it behind a pager. Pages are read one after another, `pageSize` (200 when not given) rows each; a bare
   * array answer has no pages and comes back as it is.
   */
  allPages?: boolean;
}

export interface BaseEntityLoadResponse<Entity extends BaseEntity> {
  number: number | undefined;
  size: number | undefined;
  totalElements: number | undefined;
  totalPages: number | undefined;
  content: Entity[];
}
