import { BaseEntity } from '../base-entity/base-entity';
import { map, Observable } from 'rxjs';
import { inject, Inject } from '@angular/core';
import { BaseEntityMapper } from '../base-entity.mapper';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from './base-entity-load-response';
import { buildUrl } from 'build-url-ts';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { BaseEntityService } from './base-entity.service';

export abstract class BaseEntityRestService<Entity extends BaseEntity> implements BaseEntityService<Entity> {
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);
  private readonly baseUrl: string;
  protected httpClient = inject(HttpClient);
  protected headers = new HttpHeaders({
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Methods': 'GET, HEAD, PUT, PATCH, POST, DELETE',
    'Access-Control-Allow-Origin-Credentials': 'true',
    'Access-Control-Allow-Headers': 'Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, X-BRZ-User-ID',
    'X-BRZ-User-ID': 'zsolt.zsuffa@extern.brz.gv.at',
  });

  protected constructor(
    @Inject('entityMapper') protected entityMapper: BaseEntityMapper<Entity>,
    protected urlProperty: string,
    protected resourceUrl: string,
  ) {
    this.baseUrl = Reflect.get(this.runtimeConfiguration, urlProperty);
  }

  // region public accessor and mutator methods
  delete(id: string): Observable<unknown> {
    return this.httpClient.delete(this.resourceUrl + `/${id}`, { headers: this.headers });
  }

  deleteAll(): Observable<any> {
    return this.httpClient.delete(this.resourceUrl, { headers: this.headers });
  }

  findAll(page?: number): Observable<BaseEntityLoadResponse<Entity> | Entity[] | Entity> {
    return this.findByQuery({ page });
  }

  findByQuery(queryCondition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<Entity> | Entity[] | Entity> {
    const fullUrl = this.buildFullUrl(this.resourceUrl, queryCondition);
    if (fullUrl) {
      return this.httpClient.get(fullUrl, { headers: this.headers }).pipe(
        map((httpResponse: any) => {
          return queryCondition.page ? this.mapPagedResponse(httpResponse) : this.mapSimpleResponse(httpResponse);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  findById(id: string): Observable<void | Entity> {
    const queryCondition: BaseEntityQueryCondition = { pathParams: new Map<string, string>([['id', id]]) };
    const fullUrl = this.buildFullUrl(this.resourceUrl, queryCondition);
    if (fullUrl) {
      return this.httpClient.get(fullUrl, { headers: this.headers }).pipe(
        map((httpResponse: any) => {
          return this.mapSimpleResponse(httpResponse) as Entity;
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  add(entity: Entity, id?: number): Observable<Entity> {
    const dto = this.entityMapper.toDto(entity);
    const fullUrl = this.buildFullUrl(this.resourceUrl, {});
    if (fullUrl) {
      return this.httpClient.post(fullUrl, dto, { headers: this.headers }).pipe(
        map((response: any) => {
          return this.entityMapper.fromDto(response, id);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  update(entity: Entity): Observable<Entity> {
    const dto = this.entityMapper.toDto(entity);
    const pathParams = new Map<string, string>([['id', String(entity.id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}', { pathParams });
    if (fullUrl) {
      return this.httpClient.put(fullUrl, dto, { headers: this.headers }).pipe(
        map((response: any) => {
          return this.entityMapper.fromDto(response);
        }),
      );
    } else throw new Error('Could not determine the full url');
  }

  // endregion

  // region protected, private helper methods
  protected buildFullUrl(resourceUri: string, queryCondition: BaseEntityQueryCondition): string | undefined {
    const queryParams: Map<string, string> = new Map<string, string>();
    if (queryCondition.page) queryParams.set('page', queryCondition.page.toString());
    if (queryCondition.filters !== undefined && queryCondition.filters.length != 0) {
      queryCondition.filters.forEach((filter) => queryParams.set(filter.property, filter.value));
    }

    let params: any = {};
    if (queryParams.size > 0) {
      for (const [key, value] of queryParams) {
        params = { ...params, [key]: value };
      }
    } else params = null;

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

  private mapPagedResponse(response: any): BaseEntityLoadResponse<Entity> {
    const subjectPage = response[0];
    const content = subjectPage.content.map((dto: any, index: number) => {
      return this.entityMapper.fromDto(dto, index);
    });
    return {
      page: subjectPage.page,
      pageSize: subjectPage.pageSize,
      totalPageCount: subjectPage.totalPageCount,
      content,
    };
  }

  private mapSimpleResponse(response: any): Entity[] | Entity {
    if (Object.prototype.toString.call(response) === '[object Array]') {
      return response.map((dto: any, index: number) => {
        return this.entityMapper.fromDto(dto, index);
      });
    } else {
      return new Array(this.entityMapper.fromDto(response));
    }
  }

  // endregion
}
