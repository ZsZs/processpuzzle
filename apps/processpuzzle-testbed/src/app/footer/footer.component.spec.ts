import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FooterComponent } from './footer.component';
import { By } from '@angular/platform-browser';

describe('FooterComponent', () => {
  let component: FooterComponent;
  let fixture: ComponentFixture<FooterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FooterComponent],
      //      providers: [provideFirestore(() => getFirestore())],
    }).compileComponents();

    fixture = TestBed.createComponent(FooterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-toolbar', () => {
    const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
    expect(matToolbar).toBeTruthy();
  });
});
