import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestFormsComponent } from './test-forms.component';
import { provideRouter } from '@angular/router';

describe('BaseFormsComponent', () => {
  let component: TestFormsComponent;
  let fixture: ComponentFixture<TestFormsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestFormsComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestFormsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
