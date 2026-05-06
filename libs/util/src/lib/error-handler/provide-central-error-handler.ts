import { EnvironmentProviders, ErrorHandler, makeEnvironmentProviders } from '@angular/core';
import { CentralErrorHandler } from './central-error-handler';

export function provideCentralErrorHandler(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: ErrorHandler, useClass: CentralErrorHandler }]);
}
