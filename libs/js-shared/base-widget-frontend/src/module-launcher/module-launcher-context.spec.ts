import { describe, expect, it } from 'vitest';
import { launcherCardsOf, ModuleLauncherEntry } from './module-launcher-context';

const STOCK: ModuleLauncherEntry = {
  moduleKey: 'inv-stock',
  name: 'Stock',
  basePath: 'stock',
  image: 'module:launcher.svg',
  titleKey: 'launcher.title',
  subtitleKey: 'launcher.subtitle',
  contentKeys: ['launcher.content', 'launcher.more'],
  link: 'overview',
};

describe('launcherCardsOf', () => {
  it('turns an entry and its texts into a card that leads to the mount, climbing out of the page it sits on', () => {
    const [card] = launcherCardsOf(
      [STOCK],
      [{ 'launcher.title': 'Stock keeping', 'launcher.subtitle': 'Items and places', 'launcher.content': 'Track items.', 'launcher.more': 'Counts' }],
      ['blob:image'],
      1,
    );

    expect(card).toEqual({
      title: 'Stock keeping',
      subtitle: 'Items and places',
      content: ['Track items.', 'Counts'],
      actions: [],
      translocoPrefix: '',
      link: '../stock/overview',
      backgroundImage: 'blob:image',
    });
  });

  it('is titled with the module name, and leaves out what has no text, when nothing is translated', () => {
    const [card] = launcherCardsOf([STOCK], [{}], [undefined], 1);

    expect(card.title).toBe('Stock');
    expect(card.subtitle).toBe('');
    expect(card.content).toEqual([]);
    expect(card.backgroundImage).toBeUndefined();
  });

  it('keeps the texts that exist when only some do', () => {
    const [card] = launcherCardsOf([STOCK], [{ 'launcher.more': 'Counts' }], [], 1);

    expect(card.content).toEqual(['Counts']);
  });

  it('links to the mount itself when the card names no route, and relative to a page with several segments', () => {
    const [card] = launcherCardsOf([{ ...STOCK, link: undefined }], [{}], [], 2);

    expect(card.link).toBe('../../stock');
  });

  it('links from an index page without climbing, and tolerates a leading slash in the route', () => {
    const [card] = launcherCardsOf([{ ...STOCK, link: '/overview' }], [{}], [], 0);

    expect(card.link).toBe('stock/overview');
  });

  it('answers one card per entry, in order, even before any text or picture has arrived', () => {
    const orders: ModuleLauncherEntry = { ...STOCK, moduleKey: 'inv-orders', name: 'Orders', basePath: 'orders', link: undefined };

    const cards = launcherCardsOf([STOCK, orders], [], [], 1);

    expect(cards.map((card) => card.title)).toEqual(['Stock', 'Orders']);
    expect(cards.map((card) => card.link)).toEqual(['../stock/overview', '../orders']);
  });
});
