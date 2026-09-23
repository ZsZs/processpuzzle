import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SidenavAutosizeDirective } from './sidenav-autosize.directive';

@Component({
  imports: [MatSidenavContainer, MatSidenav, MatSidenavContent, SidenavAutosizeDirective],
  template: `
    <mat-sidenav-container>
      <mat-sidenav mode="side" opened ppSidenavAutosize>nav</mat-sidenav>
      <mat-sidenav-content>content</mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
class HostComponent {}

describe('SidenavAutosizeDirective', () => {
  let resizeCallback: ResizeObserverCallback | undefined;
  const observe = vi.fn();
  const disconnect = vi.fn();

  beforeEach(() => {
    resizeCallback = undefined;
    observe.mockClear();
    disconnect.mockClear();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          resizeCallback = callback;
        }
        observe = observe;
        disconnect = disconnect;
      },
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  it('observes the drawer element once rendered', async () => {
    const fixture = await render();

    expect(observe).toHaveBeenCalledWith(fixture.debugElement.query(By.directive(MatSidenav)).nativeElement);
  });

  it('recomputes the content margins when the drawer resizes', async () => {
    const fixture = await render();
    const container = fixture.debugElement.query(By.directive(MatSidenavContainer)).componentInstance as MatSidenavContainer;
    const update = vi.spyOn(container, 'updateContentMargins');

    resizeCallback?.([], {} as ResizeObserver);

    expect(update).toHaveBeenCalled();
  });

  it('disconnects when destroyed', async () => {
    const fixture = await render();

    fixture.destroy();

    expect(disconnect).toHaveBeenCalled();
  });
});
