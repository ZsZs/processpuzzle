import { BaseEntityRestService } from '../base-entity-service/base-entity-rest.service';
import { DynamicEntity } from './entity-definition';
import { DynamicEntityMapper } from './dynamic-entity.mapper';
import { ENTITY_SERVICE_ROOT_KEY } from './entity-definition.service';

/**
 * The repository of one metadata-defined entity type —
 * `/organizations/{orgKey}/entities/{entityDefinitionCode}`.
 *
 * A plain `BaseEntityRestService`, which is the point: the operation layer of the contract is an ordinary
 * paged collection with an RSQL filter, so nothing about reading, filtering, paging or writing needs new
 * code. Two details differ from the rest of the workspace, and only two:
 *
 * - **The collection is per definition.** `entities/order` and `entities/dynamic-entity` are different
 *   resources, so the definition code is part of the resource URL rather than a query parameter, and one
 *   service instance serves one entity type — exactly as a hand-written service does.
 * - **The query parameters are named differently.** `listEntities` in `base-entity-api.yaml` calls them
 *   `rsql` and `sort` where `shared-api.yaml` and every feature built on it say `where` and `order`. The
 *   expressions are the same; only the names differ, hence the two overrides.
 *
 * Constructed directly by `DynamicEntityFacade` rather than provided, so the definition code can be a
 * constructor argument. It must be constructed inside an injection context — the base class injects
 * `HttpClient` and `RUNTIME_CONFIGURATION` in field initializers.
 */
export class DynamicEntityService extends BaseEntityRestService<DynamicEntity> {
  protected override readonly filterParamName = 'rsql';
  protected override readonly sortParamName = 'sort';

  constructor(entityDefinitionCode: string) {
    super(new DynamicEntityMapper(entityDefinitionCode), ENTITY_SERVICE_ROOT_KEY, `entities/${entityDefinitionCode}`);
  }
}
