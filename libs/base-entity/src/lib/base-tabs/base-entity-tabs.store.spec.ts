import { TestBed } from '@angular/core/testing';
import { BaseEntityTabsStore } from './base-entity-tabs.store';
import { signalStore } from '@ngrx/signals';

describe('BaseEntityTabsStore', () => {
  const TabsStore = signalStore({ providedIn: 'root' }, BaseEntityTabsStore());
  let store: any;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [TabsStore],
    }).compileComponents();

    store = TestBed.inject(TabsStore);
  });

  afterEach(() => {
    store.reset();
  });

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  it('tabIsActive() adds tab to activeTabs[] array and sets the last as current', () => {
    store.tabIsActive('newTab_1');
    expect(store.activeTabs().length).toBe(1);
    expect(store.activeTabs()).toContain('newTab_1');
    expect(store.currentTab()).toEqual('newTab_1');

    store.tabIsActive('newTab_2');
    expect(store.activeTabs().length).toBe(2);
    expect(store.activeTabs()).toContain('newTab_2');
    expect(store.currentTab()).toEqual('newTab_2');
  });

  it('tabIsActive() adds tab only, when tab name does not exist', () => {
    store.reset();
    store.tabIsActive('newTab');
    store.tabIsActive('newTab');
    expect(store.activeTabs().length).toEqual(1);
  });

  it('tabIsInActive() removes to ', () => {
    // SETUP:
    store.reset();
    store.tabIsActive('newTab_1');
    expect(store.activeTabs().length).toEqual(1);

    // EXERCISE:
    store.tabIsInactive('newTab_1');

    // VERIFY:
    expect(store.activeTabs().length).toEqual(0);
  });
});
