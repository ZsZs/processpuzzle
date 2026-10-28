import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { MODULE_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { ASSETS_CONTROL_STYLES, ASSETS_CONTROL_TEMPLATE, AssetsControlBase } from './assets-control.base';
import { ModuleAssetService } from './module-asset.service';

/** Key root of the control's own words. */
export const MODULE_ASSETS_I18N_SCOPE = `${MODULE_DEFINITION_I18N_SCOPE}.assets_panel`;

/**
 * The module form's Assets section. Like the translations section it is not a form field — nothing here is saved
 * with the module — so it is disabled and the form never submits it.
 */
export class ModuleAssetsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor() {
    super('assets', FormControlType.CUSTOM, 'Assets');
    this.component = ModuleAssetsControlComponent;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/** The files a module brings along; a widget names one as `module:<path>`, a markdown file links the others relatively. */
@Component({
  selector: 'pp-module-assets-control',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, TranslocoPipe],
  template: ASSETS_CONTROL_TEMPLATE,
  styles: [ASSETS_CONTROL_STYLES],
})
export class ModuleAssetsControlComponent<Entity extends BaseEntity> extends AssetsControlBase<Entity> {
  protected readonly scope = MODULE_ASSETS_I18N_SCOPE;
  protected readonly scheme = 'module';
  protected readonly testIdPrefix = 'module';
  protected readonly service = inject(ModuleAssetService);
}
