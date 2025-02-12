import { InjectionToken, Type } from '@angular/core';
import { ConfigurationOptions } from './configuration.options';

export const CONFIGURATION_APP_INITIALIZER = new InjectionToken<(() => Promise<unknown>)[]>('CONFIGURATION_APP_INITIALIZER');
export const CONFIGURATION_TYPE = new InjectionToken<Type<unknown>>('CONFIGURATION_TYPE');
export const CONFIGURATION_OPTIONS = new InjectionToken<ConfigurationOptions>('CONFIGURATION_OPTIONS');
export const RUNTIME_CONFIGURATION = new InjectionToken<object>('RUNTIME_CONFIGURATION');
