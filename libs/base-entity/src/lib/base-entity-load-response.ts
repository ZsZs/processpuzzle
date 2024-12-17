import { BaseEntity } from './base-entity/base-entity';

export interface BaseEntityLoadResponse<Entity extends BaseEntity> {
  page: number | undefined;
  pageSize: number;
  totalPageCount: number;
  content: Entity[];
}
