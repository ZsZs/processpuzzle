import { BaseEntity } from '../../base-entity/base-entity';

export interface LookupTable extends BaseEntity {
  key: string;
  value: string | number;
  description?: string;
}
