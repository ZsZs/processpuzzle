import { beforeEach, describe, expect, it } from 'vitest';
import { BreakpointObserver } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { MockBreakpointObserver, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { LayoutService } from '@processpuzzle/util';
import { createAppRoutes } from '../../app.routes';
import { SidenavComponent } from './sidenav.component';

describe('SidenavComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: { navigation: { home: 'Home', admin: { users: 'Users' } } } } };
  let component: SidenavComponent;
  let fixture: ComponentFixture<SidenavComponent>;
  let breakpointObserver: MockBreakpointObserver;
  let layoutService: LayoutService;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(SidenavComponent, testConfig, {
      providers: [
        provideRouter(createAppRoutes('acme')),
        { provide: BreakpointObserver, useClass: MockBreakpointObserver },
        { provide: LayoutService, useClass: LayoutService, deps: [BreakpointObserver] },
      ],
    });
    component = result.component;
    fixture = result.fixture;
    layoutService = TestBed.inject(LayoutService);
    breakpointObserver = TestBed.inject(BreakpointObserver) as unknown as MockBreakpointObserver;
    breakpointObserver.resize(800);
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-nav-list, one item per titled route', () => {
    expect(layoutService.isSmallDevice()).toBeFalsy();
    expect(fixture.debugElement.query(By.css('mat-nav-list')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.queryAll(By.css('mat-list-item'))).toHaveLength(component.routes.length);
  });

  it('renders the translated label, untrimmed', async () => {
    await fixture.whenStable();
    const labels = fixture.debugElement.queryAll(By.css('[matListItemTitle]')).map((item) => (item.nativeElement as HTMLElement).textContent?.trim());
    expect(labels).toEqual(['Home', 'Users']);
  });

  it('empty, if its small device', () => {
    breakpointObserver.resize(599);
    fixture.detectChanges();
    expect(layoutService.isSmallDevice()).toBeTruthy();
    expect(fixture.debugElement.query(By.css('mat-nav-list'))).toBeNull();
  });
});
