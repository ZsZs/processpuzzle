import { beforeEach, describe, expect, it } from 'vitest';
import { ANIMATION_MODULE_TYPE, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { LayoutService } from '@processpuzzle/util';
import { AppComponent } from './app.component';
import { FooterComponent } from './navigation/footer/footer.component';
import { HeaderComponent } from './navigation/header/header.component';
import { SidenavComponent } from './navigation/sidenav/sidenav.component';

@Component({ selector: 'app-header', template: '' })
class MockHeaderComponent {}

@Component({ selector: 'app-sidenav', template: '' })
class MockSidenavComponent {}

@Component({ selector: 'app-footer', template: '' })
class MockFooterComponent {}

describe('AppComponent', () => {
  let component: AppComponent;
  let fixture: ComponentFixture<AppComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [LayoutService, provideRouter([]), { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' }],
    })
      .overrideComponent(AppComponent, {
        remove: { imports: [HeaderComponent, SidenavComponent, FooterComponent] },
        add: { imports: [MockHeaderComponent, MockSidenavComponent, MockFooterComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: app-header, mat-sidenav-container, app-footer', () => {
    expect(fixture.debugElement.query(By.css('app-header')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('mat-sidenav-container')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-footer')).nativeElement).toBeTruthy();
  });

  it('toggleSidenav flips the sidenav open state', () => {
    expect(component.sidenavOpened()).toBeTruthy();
    component.toggleSidenav();
    expect(component.sidenavOpened()).toBeFalsy();
    component.toggleSidenav();
    expect(component.sidenavOpened()).toBeTruthy();
  });
});
