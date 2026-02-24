import { inject, Injectable, Injector } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { CONFIGURATION_OPTIONS } from './configuration.injection-tokens';
import { lastValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ConfigurationService<TConfiguration> {
  private config?: TConfiguration;
  private currentConfigUrl: string | undefined;
  private readonly httpClient = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly configurationOptions = inject(CONFIGURATION_OPTIONS);

  // region public accessors and mutators
  public get configuration(): TConfiguration {
    if (this.config == null) throw new Error("Configuration hasn't been initialized");
    return this.config;
  }

  public async init(): Promise<void> {
    try {
      await this.initInternal();
    } catch (error: any) {
      throw new Error(`Runtime configuration:${this.currentConfigUrl} load failed - ${error.message}`);
    }
  }

  // endregion

  // region protected, private helper methods
  private async initInternal(): Promise<void> {
    const externalUrls = this.determineConfigUrls();
    const configs = await this.loadConfigs(externalUrls);
    this.config = this.mergeConfigs(configs);

    if (this.configurationOptions?.log === true) {
      console.log('Configuration loaded', this.config);
    }
  }

  private mergeConfigs(configs: TConfiguration[]): TConfiguration {
    return { ...configs[0], ...configs[1] };
  }

  private loadConfig(url: string): Promise<TConfiguration> {
    this.currentConfigUrl = url;
    const config$ = this.httpClient.get<TConfiguration>(url);
    return lastValueFrom(config$);
  }

  private async loadConfigs(configUrls: string[]): Promise<TConfiguration[]> {
    const configs$ = new Array<Promise<TConfiguration>>();
    configUrls.forEach((url) => {
      const config$ = this.loadConfig(url);
      configs$.push(config$);
    });
    const results: TConfiguration[] = [];
    for (const configPromise of configs$) {
      try {
        const config = await configPromise;
        results.push(config);
      } catch (error) {
        if (error instanceof HttpErrorResponse) {
          // Handle HttpErrorResponse - skip this config
          continue;
        }
        throw error;
      }
    }
    return results;
  }

  private determineConfigUrls() {
    const urls = this.getUrls();
    return urls.map((x) => this.ensureExternalUrl(x));
  }

  private ensureExternalUrl(url: string): string {
    if (url.startsWith('//') || url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }

    if (url.startsWith('/')) url = url.substring(1);

    const baseHref = globalThis.location.origin;
    return `${baseHref}/${url}`;
  }

  private getUrls(): string[] {
    if (this.configurationOptions?.urlFactory == null) return ['config.common.json'];

    const result = this.configurationOptions.urlFactory(this.injector);
    if (typeof result === 'string') return [result];
    if (Array.isArray(result)) return result;
    throw new Error('Unexpected value returned from ConfigurationUrlFactory');
  }

  // endregion
}
