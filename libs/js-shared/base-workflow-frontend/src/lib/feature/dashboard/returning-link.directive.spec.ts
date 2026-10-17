import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BaseFormNavigatorSingletonStore } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReturningLinkDirective } from './returning-link.directive';

@Component({ standalone: true, template: '' })
class BlankComponent {}

@Component({
  standalone: true,
  imports: [ReturningLinkDirective],
  template: `<a ppReturningLink="/document/c-1/sign" [queryParams]="{ subjectType: 'employee', subjectId: 'e-1' }">Contract</a>`,
})
class HostComponent {}

describe('ReturningLinkDirective', () => {
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideRouter([
          { path: 'dashboard', component: BlankComponent },
          { path: '**', component: BlankComponent },
        ]),
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    await router.navigateByUrl('/dashboard?task=t-1');
  });

  const anchor = (): HTMLAnchorElement => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('a') as HTMLAnchorElement;
  };

  it('keeps the real URL as href, so the link can still open in a new tab', () => {
    expect(anchor().getAttribute('href')).toBe('/document/c-1/sign?subjectType=employee&subjectId=e-1');
  });

  // The defect this exists for: a routerLink left `returnTo` empty, and Cancel landed on the record's list.
  it('opens the target through the navigator, which remembers the dashboard as where to return to', async () => {
    const navigator = TestBed.inject(BaseFormNavigatorSingletonStore);

    anchor().dispatchEvent(new MouseEvent('click', { button: 0, bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(router.url).toBe('/document/c-1/sign?subjectType=employee&subjectId=e-1'));

    expect(navigator.returnTo()).toBe('/dashboard?task=t-1');
  });

  it('leaves a modified click to the browser', () => {
    const navigator = TestBed.inject(BaseFormNavigatorSingletonStore);
    const navigateToUrl = vi.spyOn(navigator, 'navigateToUrl');
    const event = new MouseEvent('click', { button: 0, ctrlKey: true, bubbles: true, cancelable: true });

    anchor().dispatchEvent(event);

    expect(navigateToUrl).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(false);
  });
});
