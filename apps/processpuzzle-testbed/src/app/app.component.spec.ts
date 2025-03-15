import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { LayoutService } from '@processpuzzle/util';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent, NoopAnimationsModule, provideRouter([])],
      providers: [LayoutService],
    }).compileComponents();
    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('app-header, app-footer, mat-sidenav-container:', () => {
    const appHeader = fixture.debugElement.query(By.css('app-header')).nativeElement;
    expect(appHeader).toBeTruthy();

    const sideNavContainer = fixture.debugElement.query(By.css('mat-sidenav-container')).nativeElement;
    expect(sideNavContainer).toBeTruthy();

    const appFooter = fixture.debugElement.query(By.css('app-footer')).nativeElement;
    expect(appFooter).toBeTruthy();
  });
});
