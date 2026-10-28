import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { AppAssetService } from './app-asset.service';
import { ASSETS_CONTROL_STYLES, ASSETS_CONTROL_TEMPLATE, AssetsControlBase } from './assets-control.base';

/** Key root of the control's own words. */
export const APP_ASSETS_I18N_SCOPE = `${APP_DEFINITION_I18N_SCOPE}.assets_panel`;

/**
 * The application form's Assets section. Like the module form's it is not a form field — nothing here is saved
 * with the application — so it is disabled and the form never submits it.
 */
export class AppAssetsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor() {
    super('assets', FormControlType.CUSTOM, 'Assets');
    this.component = AppAssetsControlComponent;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/** The files an application brings along — the pictures of its own pages, say; a widget names one as `app:<path>`. */
@Component({
  selector: 'pp-app-assets-control',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, TranslocoPipe],
  template: ASSETS_CONTROL_TEMPLATE,
  styles: [ASSETS_CONTROL_STYLES],
})
export class AppAssetsControlComponent<Entity extends BaseEntity> extends AssetsControlBase<Entity> {
  protected readonly scope = APP_ASSETS_I18N_SCOPE;
  protected readonly scheme = 'app';
  protected readonly testIdPrefix = 'app';
  protected readonly service = inject(AppAssetService);
}
