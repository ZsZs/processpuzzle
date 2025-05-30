import { ApplicationPropertyStore } from './app-property.store';
import { EnvironmentInjector, Provider } from '@angular/core';
import { ApplicationPropertyService } from './app-property.service';
import { ApplicationPropertyMapper } from './app-property.mapper';

export function provideAppPropertyStore(): Provider[] {
  return [
    { provide: ApplicationPropertyService, useFactory: () => new ApplicationPropertyService(new ApplicationPropertyMapper()), deps: [EnvironmentInjector] },
    { provide: ApplicationPropertyStore, useFactory: () => new ApplicationPropertyStore(), deps: [ApplicationPropertyService] },
  ];
}
