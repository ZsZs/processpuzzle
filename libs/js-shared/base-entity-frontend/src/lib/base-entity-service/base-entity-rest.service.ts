import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { map, Observable } from 'rxjs';
import { inject, Inject } from '@angular/core';
import { BaseEntityMapper } from '../base-entity.mapper';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from './base-entity-load-response';
import { buildUrl } from 'build-url-ts';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { BaseEntityService } from './base-entity.service';
import { toRsql } from './rsql';

export class BaseEntityRestService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);
  private readonly baseUrl: string;
  protected httpClient = inject(HttpClient);
  protected headers = new HttpHeaders({
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Methods': 'GET, HEAD, PUT, PATCH, POST, DELETE',
    'Access-Control-Allow-Origin-Credentials': 'true',
    'Access-Control-Allow-Headers': 'Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers',
  });

  constructor(
    @Inject('entityMapper') protected entityMapper: BaseEntityMapper<Entity>,
    protected urlProperty: string,
    protected resourceUrl: string,
  ) {
    const baseConf = Reflect.get(this.runtimeConfiguration, 'BASE_CONFIGURATION');
    this.baseUrl = Reflect.get(baseConf, urlProperty);
  }

  // region public accessor and mutator methods
  delete(id: string): Observable<unknown> {
    const pathParams = new Map<string, string>([['id', String(id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}', { pathParams });
    if (fullUrl) {
      return this.httpClient.delete(fullUrl, { headers: this.headers });
    } else throw new Error('Could not determine the full url');
  }

  deleteAll(): Observable<unknown> {
    return this.httpClient.delete(this.resourceUrl, { headers: this.headers });
  }

  findAll(page?: number): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>> {
    return this.findByQuery({ page });
  }

  findByQuery(queryCondition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>> {
    const fullUrl = this.buildFullUrl(this.resourceUrl, queryCondition);
    if (fullUrl) {
      return this.httpClient.get(fullUrl, { headers: this.headers, observe: 'response' }).pipe(
        map((httpResponse) => {
          if (httpResponse.status === 204 || this.isEmptyBody(httpResponse.body)) {
            throw new Error('The query returned no content.');
          }
          return queryCondition.page ? this.mapPagedResponse(httpResponse.body) : this.mapSimpleResponse(httpResponse.body);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  private isEmptyBody(body: unknown): boolean {
    if (body == null) return true;
    if (Array.isArray(body)) return body.length === 0;
    if (typeof body === 'object') return Object.keys(body as object).length === 0;
    if (typeof body === 'string') return body.length === 0;
    return false;
  }

  findById(id: string): Observable<void | PersistedEntity<Entity>> {
    const queryCondition: BaseEntityQueryCondition = { pathParams: new Map<string, string>([['id', id]]) };
    const fullUrl = this.buildFullUrl(this.resourceUrl, queryCondition);
    if (fullUrl) {
      return this.httpClient.get(fullUrl, { headers: this.headers }).pipe(
        map((httpResponse: unknown) => {
          return this.mapEntityResponse(httpResponse);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  add(entity: Entity, id?: number): Observable<PersistedEntity<Entity>> {
    const dto = this.entityMapper.toDto(entity);
    const fullUrl = this.buildFullUrl(this.resourceUrl, {});
    if (fullUrl) {
      return this.httpClient.post(fullUrl, dto, { headers: this.headers }).pipe(
        map((response: unknown) => {
          return this.mapEntityResponse(response, id);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  update(entity: PersistedEntity<Entity>): Observable<PersistedEntity<Entity>> {
    const dto = this.entityMapper.toDto(entity);
    const pathParams = new Map<string, string>([['id', String(entity.id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}', { pathParams });
    if (fullUrl) {
      return this.httpClient.put(fullUrl, dto, { headers: this.headers }).pipe(
        map((response: unknown) => {
          return this.mapEntityResponse(response);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  // endregion

  // region protected, private helper methods
  protected buildFullUrl(resourceUri: string, queryCondition: BaseEntityQueryCondition): string | undefined {
    const queryParams: Map<string, string> = new Map<string, string>();
    if (queryCondition.page) queryParams.set('page', queryCondition.page.toString());
    const rsql = this.buildRsql(queryCondition);
    if (rsql) queryParams.set('filter', rsql);

    let params: Record<string, string> | undefined = {};
    if (queryParams.size > 0) {
      for (const [key, value] of queryParams) {
        params = { ...params, [key]: value };
      }
    } else params = undefined;

    if (queryCondition.pathParams !== undefined && queryCondition.pathParams.size != 0) {
      queryCondition.pathParams.forEach((value: string, key: string) => {
        const pathParam = '%{' + key + '}';
        resourceUri = resourceUri.replace(pathParam, value);
      });
    }

    const urlOptions = {
      path: resourceUri,
      hash: queryCondition.hash,
      queryParams: params,
    };
    return buildUrl(this.baseUrl, urlOptions);
  }

  protected buildRsql(queryCondition: BaseEntityQueryCondition): string | undefined {
    if (queryCondition.query) return queryCondition.query;
    if (queryCondition.filterGroup) return toRsql(queryCondition.filterGroup);
    if (queryCondition.filters?.length) return toRsql(queryCondition.filters);
    return undefined;
  }

  private mapPagedResponse(response: unknown): BaseEntityLoadResponse<PersistedEntity<Entity>> {
    const pages = response as Array<{ content: unknown[]; page: number; pageSize: number; totalPageCount: number }>;
    const subjectPage = pages[0];
    const content = subjectPage.content.map((dto, index) => {
      return this.mapEntityResponse(dto, index);
    });
    return {
      page: subjectPage.page,
      pageSize: subjectPage.pageSize,
      totalPageCount: subjectPage.totalPageCount,
      content,
    };
  }

  private mapSimpleResponse(response: unknown): PersistedEntity<Entity>[] | PersistedEntity<Entity> {
    if (Array.isArray(response)) {
      return response.map((dto, index) => {
        return this.mapEntityResponse(dto, index);
      });
    } else {
      return new Array(this.mapEntityResponse(response));
    }
  }

  private mapEntityResponse(response: unknown, index?: number): PersistedEntity<Entity> {
    const entity = this.entityMapper.fromDto(response, index);
    assertPersistedEntity(entity);
    return entity;
  }

  // endregion
}
