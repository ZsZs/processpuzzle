import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HeaderComponent } from './header.component';
import { By } from '@angular/platform-browser';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('sanity test', () => {
    it('Should create component', () => {
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('mat-toolbar:', () => {
      const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
      expect(matToolbar).toBeTruthy();
    });
  });
});
