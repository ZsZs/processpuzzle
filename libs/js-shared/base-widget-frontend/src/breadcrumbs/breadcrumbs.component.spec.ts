import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, Routes } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it } from 'vitest';
import { BreadcrumbsComponent } from './breadcrumbs.component';

@Component({ template: '' })
class PageComponent {}

const routes: Routes = [
  {
    path: 'sailing',
    title: 'Sailing',
    children: [
      {
        path: 'races',
        data: { breadcrumb: 'Races' },
        children: [
          { path: ':raceId', children: [{ path: 'results', component: PageComponent, title: 'Results' }] },
          { path: 'hidden', component: PageComponent, data: { breadcrumb: false }, title: 'Hidden' },
        ],
      },
      { path: 'orders', component: PageComponent, data: { entityName: 'Order' }, title: 'Orders', children: [{ path: ':id', component: PageComponent, title: 'Deeper' }] },
    ],
  },
];

async function setup(url: string, inputs: { showHome?: boolean; maxItems?: number } = {}) {
  TestBed.configureTestingModule({ providers: [provideRouter(routes), provideTranslocoTesting({ translations: {} })] });
  const router = TestBed.inject(Router);
  const fixture = TestBed.createComponent(BreadcrumbsComponent);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  await router.navigateByUrl(url);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  return { fixture, router, el, texts: () => [...el.querySelectorAll('li')].map((li) => li.textContent?.replace('chevron_right', '').trim()) };
}

describe('BreadcrumbsComponent', () => {
  it('shows a crumb per labelled route, linking each to its own URL', async () => {
    const { el, texts } = await setup('/sailing/races/42/results');

    expect(texts().slice(1)).toEqual(['Sailing', 'Races', 'Results']);
    const links = [...el.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(links.slice(1)).toEqual(['/sailing', '/sailing/races']);
  });

  it('marks the last crumb as the current page, not a link', async () => {
    const { el } = await setup('/sailing/races/42/results');

    expect(el.querySelector('[aria-current="page"]')?.textContent).toBe('Results');
    expect(el.querySelectorAll('a').length).toBe(3);
  });

  it('leaves out a route whose breadcrumb is false', async () => {
    const { texts } = await setup('/sailing/races/hidden');

    expect(texts().slice(1)).toEqual(['Sailing', 'Races']);
  });

  it('stops at the route that owns an entity', async () => {
    const { texts } = await setup('/sailing/orders/7');

    expect(texts().slice(1)).toEqual(['Sailing', 'Orders']);
  });

  it('follows navigation', async () => {
    const { fixture, router, texts } = await setup('/sailing/races/42/results');

    await router.navigateByUrl('/sailing/orders');
    fixture.detectChanges();

    expect(texts().slice(1)).toEqual(['Sailing', 'Orders']);
  });

  it('omits the home crumb when asked', async () => {
    const { texts } = await setup('/sailing/races/42/results', { showHome: false });

    expect(texts()).toEqual(['Sailing', 'Races', 'Results']);
  });

  it('collapses the middle of a long trail, keeping the first and last crumbs', async () => {
    const { texts } = await setup('/sailing/races/42/results', { maxItems: 3 });

    expect(texts().slice(1)).toEqual(['…', 'Results']);
  });

  it('shows the whole trail when it fits the limit', async () => {
    const { texts } = await setup('/sailing/races/42/results', { showHome: false, maxItems: 3 });

    expect(texts()).toEqual(['Sailing', 'Races', 'Results']);
  });

  it('renders nothing when no route has a label and home is off', async () => {
    const { el } = await setup('/', { showHome: false });

    expect(el.querySelector('nav')).toBeNull();
  });
});
