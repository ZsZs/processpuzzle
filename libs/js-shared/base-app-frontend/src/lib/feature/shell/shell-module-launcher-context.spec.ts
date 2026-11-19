import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AppDefinition, ModuleMount } from '../../domain/app-definition';
import { ModuleAssetService } from '../../domain/module-asset.service';
import { ModuleDefinition } from '../../domain/module-definition';
import { ModuleTranslationService } from '../../domain/module-translation.service';
import { ShellApplicationContext } from './shell-application-context';
import { launcherEntriesOf, revisionOf, ShellModuleLauncherContext } from './shell-module-launcher-context';

const LAUNCHER = { image: 'module:launcher.svg', titleKey: 'launcher.title', subtitleKey: 'launcher.subtitle', contentKeys: ['launcher.content'], link: 'overview' };

function definition(modules: ModuleMount[], mountedModules?: ModuleDefinition[]): AppDefinition {
  return new AppDefinition({ id: 'app', name: 'Acme', modules, mountedModules });
}

describe('launcherEntriesOf', () => {
  it('is one entry per mount that has a launcher card, in mount order, named for the released module', () => {
    const entries = launcherEntriesOf(
      definition(
        [
          new ModuleMount({ moduleKey: 'inv-stock', basePath: 'stock', launcher: LAUNCHER }),
          new ModuleMount({ moduleKey: 'plain', basePath: 'plain' }),
          new ModuleMount({ moduleKey: 'inv-orders', basePath: 'orders', launcher: { titleKey: 'launcher.title' } }),
        ],
        [new ModuleDefinition({ id: 'inv-stock', name: 'Stock' }), new ModuleDefinition({ id: 'inv-orders', name: 'Orders' })],
      ),
    );

    expect(entries).toEqual([
      {
        moduleKey: 'inv-stock',
        name: 'Stock',
        basePath: 'stock',
        image: 'module:launcher.svg',
        titleKey: 'launcher.title',
        subtitleKey: 'launcher.subtitle',
        contentKeys: ['launcher.content'],
        link: 'overview',
      },
      { moduleKey: 'inv-orders', name: 'Orders', basePath: 'orders', titleKey: 'launcher.title', contentKeys: [] },
    ]);
  });

  it("falls back to the module key where the definition does not carry its modules, as the designer's read does", () => {
    const [entry] = launcherEntriesOf(definition([new ModuleMount({ moduleKey: 'inv-stock', basePath: 'stock', launcher: LAUNCHER })]));

    expect(entry.name).toBe('inv-stock');
  });

  it('is empty without a definition, mounts, or a title to draw', () => {
    expect(launcherEntriesOf(undefined)).toEqual([]);
    expect(launcherEntriesOf(definition([]))).toEqual([]);
    expect(launcherEntriesOf(definition([new ModuleMount({ moduleKey: 'a', basePath: 'a', launcher: { titleKey: '' } })]))).toEqual([]);
  });
});

describe('revisionOf', () => {
  it('reads the released modules in the running application and the drafts in the designer', () => {
    expect(revisionOf(definition([], []))).toBe('released');
    expect(revisionOf(definition([]))).toBe('draft');
    expect(revisionOf(undefined)).toBe('draft');
  });
});

describe('ShellModuleLauncherContext', () => {
  function setUp(shellDefinition: AppDefinition) {
    const bundle = vi.fn((moduleKey: string, locale: string) =>
      moduleKey === 'broken' ? throwError(() => new Error('offline')) : of({ 'launcher.title': `${moduleKey} ${locale}` }),
    );
    const content = vi.fn(() => of(new Blob(['<svg/>'])));
    TestBed.configureTestingModule({
      providers: [
        ShellApplicationContext,
        ShellModuleLauncherContext,
        { provide: ModuleTranslationService, useValue: { bundle } },
        { provide: ModuleAssetService, useValue: { content } },
      ],
    });
    TestBed.inject(ShellApplicationContext).definition.set(shellDefinition);
    return { context: TestBed.inject(ShellModuleLauncherContext), bundle, content };
  }

  it('derives its entries from the definition the shell renders, and follows it', () => {
    const { context } = setUp(definition([new ModuleMount({ moduleKey: 'inv-stock', basePath: 'stock', launcher: LAUNCHER })]));
    expect(context.entries().map((entry) => entry.moduleKey)).toEqual(['inv-stock']);

    TestBed.inject(ShellApplicationContext).definition.set(definition([]));

    expect(context.entries()).toEqual([]);
  });

  it('reads texts and pictures in the channel of the definition — released for the running application', async () => {
    const { context, bundle, content } = setUp(definition([], []));

    expect(await firstValueFrom(context.texts('inv-stock', 'de'))).toEqual({ 'launcher.title': 'inv-stock de' });
    await firstValueFrom(context.assetBlob('inv-stock', 'launcher.svg'));

    expect(bundle).toHaveBeenCalledWith('inv-stock', 'de', 'released');
    expect(content).toHaveBeenCalledWith('inv-stock', 'launcher.svg', 'released');
  });

  it('reads the drafts in the designer, and answers no texts for a module whose translations cannot be read', async () => {
    const { context, bundle } = setUp(definition([]));

    await firstValueFrom(context.texts('inv-stock', 'en'));
    expect(bundle).toHaveBeenCalledWith('inv-stock', 'en', 'draft');
    expect(await firstValueFrom(context.texts('broken', 'en'))).toEqual({});
  });
});
