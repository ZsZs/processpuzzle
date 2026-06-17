import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { SidenavComponent } from './sidenav.component';
import { LayoutService } from '@processpuzzle/util';
import { MockBreakpointObserver, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('SidenavComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: SidenavComponent;
  let fixture: ComponentFixture<SidenavComponent>;
  let breakpointObserver: MockBreakpointObserver;
  let layoutService: LayoutService;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(SidenavComponent, testConfig, {
      providers: [
        provideRouter([]),
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
    expect(layoutService).toBeTruthy();
    expect(breakpointObserver).toBeTruthy();
  });

  it('template structure contains: mat-action-list:', () => {
    expect(layoutService.isSmallDevice()).toBeFalsy();
    const matActionList = fixture.debugElement.query(By.css('mat-nav-list')).nativeElement;
    expect(matActionList).toBeTruthy();
  });

  it('empty, if its small device', () => {
    breakpointObserver.resize(599);
    fixture.detectChanges();
    const matActionList = fixture.debugElement.query(By.css('mat-nav-list'));
    expect(layoutService.isSmallDevice()).toBeTruthy();
    expect(matActionList).toBeNull();
  });
});
