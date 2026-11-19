import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { screen, waitFor } from '@testing-library/angular';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ModuleLauncherComponent } from './module-launcher.component';
import { ModuleLauncherContext, ModuleLauncherEntry, MODULE_LAUNCHER_CONTEXT } from './module-launcher-context';

const STOCK: ModuleLauncherEntry = {
  moduleKey: 'inv-stock',
  name: 'Stock',
  basePath: 'stock',
  image: 'module:launcher.svg',
  titleKey: 'launcher.title',
  contentKeys: ['launcher.content'],
  link: 'overview',
};
const ORDERS: ModuleLauncherEntry = { moduleKey: 'inv-orders', name: 'Orders', basePath: 'orders', titleKey: 'launcher.title', contentKeys: [] };

function contextOf(
  entries: ModuleLauncherEntry[],
  texts: (moduleKey: string, lang: string) => Observable<Record<string, string>> = () => of({}),
  assetBlob: (moduleKey: string, path: string) => Observable<Blob> = () => throwError(() => new Error('no asset')),
) {
  const entriesSignal = signal(entries);
  const context: ModuleLauncherContext = { entries: entriesSignal, texts, assetBlob };
  return { context, entriesSignal };
}

async function setUp(context?: ModuleLauncherContext) {
  const providers = [provideRouter([]), ...(context ? [{ provide: MODULE_LAUNCHER_CONTEXT, useValue: context }] : [])];
  return setUpTranslocoTestBed(ModuleLauncherComponent, { translations: { en: {} } }, { providers });
}

describe('ModuleLauncherComponent', () => {
  let created: string[];
  let revoked: string[];

  beforeEach(() => {
    created = [];
    revoked = [];
    let counter = 0;
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: () => {
        const url = `blob:test/${++counter}`;
        created.push(url);
        return url;
      },
      revokeObjectURL: (url: string) => revoked.push(url),
    }));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('draws a card for every entry, titled with the module name until a text arrives', async () => {
    await setUp(contextOf([STOCK, ORDERS]).context);

    expect(await screen.findByText('Stock')).toBeTruthy();
    expect(screen.getByText('Orders')).toBeTruthy();
  });

  it("shows the module's own texts, read for the language in use", async () => {
    const texts = vi.fn((moduleKey: string, lang: string) =>
      of<Record<string, string>>(moduleKey === 'inv-stock' ? { 'launcher.title': `Stock keeping (${lang})`, 'launcher.content': 'Track items.' } : {}),
    );

    await setUp(contextOf([STOCK, ORDERS], texts).context);

    expect(await screen.findByText('Stock keeping (en)')).toBeTruthy();
    expect(screen.getByText('Track items.')).toBeTruthy();
    expect(screen.getByText('Orders')).toBeTruthy();
    expect(texts).toHaveBeenCalledWith('inv-stock', 'en');
  });

  it('draws nothing outside an application, where no context is provided', async () => {
    const { fixture } = await setUp();

    expect(fixture.nativeElement.querySelector('pp-cards-grid')).toBeNull();
  });

  it('draws nothing while no mounted module brought a card', async () => {
    const { fixture } = await setUp(contextOf([]).context);

    expect(fixture.nativeElement.querySelector('pp-cards-grid')).toBeNull();
  });

  it('keeps the card when its texts cannot be read', async () => {
    await setUp(contextOf([STOCK], () => throwError(() => new Error('offline'))).context);

    expect(await screen.findByText('Stock')).toBeTruthy();
  });

  it("fetches a card's picture from its module and puts it behind the card; the card stays without one that cannot be had", async () => {
    const assetBlob = vi.fn((moduleKey: string) => (moduleKey === 'inv-stock' ? of(new Blob(['<svg/>'])) : throwError(() => new Error('gone'))));
    const entries = [STOCK, { ...ORDERS, image: 'module:missing.svg' }];

    const { fixture } = await setUp(contextOf(entries, () => of({}), assetBlob).context);

    await waitFor(() => expect(created).toEqual(['blob:test/1']));
    expect(assetBlob).toHaveBeenCalledWith('inv-stock', 'launcher.svg');
    expect(assetBlob).toHaveBeenCalledWith('inv-orders', 'missing.svg');
    await waitFor(() => {
      fixture.detectChanges();
      const backgrounds = [...fixture.nativeElement.querySelectorAll('mat-card')].map((card) => (card as HTMLElement).style.backgroundImage);
      expect(backgrounds).toEqual(['url("blob:test/1")', '']);
    });
  });

  it('revokes the picture it made when the page goes', async () => {
    const { fixture } = await setUp(contextOf([STOCK], () => of({}), () => of(new Blob(['x']))).context);
    await waitFor(() => expect(created).toHaveLength(1));

    fixture.destroy();
    TestBed.resetTestingModule();

    expect(revoked).toContain('blob:test/1');
  });

  it('follows the entries when the application changes them', async () => {
    const { context, entriesSignal } = contextOf([STOCK]);
    const { fixture } = await setUp(context);
    expect(await screen.findByText('Stock')).toBeTruthy();

    entriesSignal.set([ORDERS]);
    fixture.detectChanges();

    expect(await screen.findByText('Orders')).toBeTruthy();
    await waitFor(() => expect(screen.queryByText('Stock')).toBeNull());
  });
});
