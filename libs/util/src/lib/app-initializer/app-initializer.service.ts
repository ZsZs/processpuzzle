import { inject, Injectable } from '@angular/core';
import { ConfigurationService } from '../runtime-configuration/configuration.service';
import { CONFIGURATION_APP_INITIALIZER, CONFIGURATION_OPTIONS } from '../runtime-configuration/configuration.injection-tokens';
import { isPromise } from '../runtime-configuration/helpers';

@Injectable({ providedIn: 'root' })
export class AppInitializer {
  private readonly appInitializers = inject(CONFIGURATION_APP_INITIALIZER);
  private readonly configurationOptions = inject(CONFIGURATION_OPTIONS);
  private readonly configService = inject(ConfigurationService);

  async init(): Promise<void> {
    if (this.configurationOptions.urlFactory) await this.configService.init();

    const all = this.appInitializers.map((p) => p());
    const promises = all.filter((x) => isPromise(x));
    if (promises.length !== 0) {
      await Promise.all(promises);
    }

    //    console.log( import.meta.env.NG_APP_PIPELINE_STAGE);
    console.log('Application initialized.');
  }
}
