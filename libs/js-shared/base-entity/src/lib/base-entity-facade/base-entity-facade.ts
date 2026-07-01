import { inject, Injectable, Injector, runInInjectionContext, Type } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { entityNameFromType } from '../base-entity/base-entity-utility';
import { BaseEntityMapper, SimpleEntityMapper } from '../base-entity.mapper';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { BaseEntityRestService } from '../base-entity-service/base-entity-rest.service';
import { BaseEntityFirestoreService } from '../base-entity-service/base-entity-firestore.service';
import { BaseEntityStore } from '../base-entity-store/base-entity.store';
import { BaseEntityTabsStore } from '../base-tabs/base-entity-tabs.store';
import { BaseEntityContainerStore } from '../base-entity-container.store';

export type EntityServiceKind = 'rest' | 'firestore';

@Injectable()
export abstract class BaseEntityFacade<Entity extends BaseEntity> {
  abstract readonly entityType: Type<Entity>;

  protected readonly serviceKind: EntityServiceKind = 'rest';
  protected readonly endpoint?: string;
  protected readonly backendRoot?: string;

  private readonly injector = inject(Injector);
  private _mapper?: BaseEntityMapper<Entity>;
  private _service?: BaseEntityService<Entity>;
  private _storeClass?: Type<unknown>;
  private _storeInstance?: unknown;
  private _descriptor?: BaseEntityDescriptor;

  get mapper(): BaseEntityMapper<Entity> {
    return (this._mapper ??= this.createMapper());
  }

  get service(): BaseEntityService<Entity> {
    return (this._service ??= this.createService(this.mapper));
  }

  get storeClass(): Type<unknown> {
    return (this._storeClass ??= this.createStoreClass());
  }

  get store(): unknown {
    return (this._storeInstance ??= runInInjectionContext(this.injector, () => inject(this.storeClass)));
  }

  get descriptor(): BaseEntityDescriptor {
    if (!this._descriptor) {
      this._descriptor = this.createDescriptor();
      this._descriptor.store = this.store;
    }
    return this._descriptor;
  }

  get entityName(): string {
    return this.descriptor.entityName;
  }

  get attrDescriptors(): AbstractAttrDescriptor[] {
    return this.descriptor.attrDescriptors;
  }

  get entityTitle(): string {
    const title = this.descriptor.entityTitle;
    return typeof title === 'function' ? title() : title;
  }

  protected createMapper(): BaseEntityMapper<Entity> {
    return new SimpleEntityMapper<Entity>(this.entityType);
  }

  protected createService(mapper: BaseEntityMapper<Entity>): BaseEntityService<Entity> {
    return runInInjectionContext(this.injector, () => {
      if (this.serviceKind === 'rest') {
        if (!this.backendRoot || !this.endpoint) {
          throw new Error(`${this.constructor.name}: REST facade requires backendRoot and endpoint`);
        }
        return new BaseEntityRestService<Entity>(mapper, this.backendRoot, this.endpoint);
      }
      if (!this.endpoint) {
        throw new Error(`${this.constructor.name}: Firestore facade requires endpoint (collection name)`);
      }
      return new BaseEntityFirestoreService<Entity>(mapper, this.endpoint);
    });
  }

  protected createStoreClass(): Type<unknown> {
    return signalStore(
      { providedIn: 'root' },
      BaseEntityStore<Entity>(this.entityType, () => this.service),
      BaseEntityTabsStore(),
      BaseEntityContainerStore(),
      withDevtools(entityNameFromType(this.entityType)),
    );
  }

  protected abstract createDescriptor(): BaseEntityDescriptor;
}
