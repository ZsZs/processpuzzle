import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestFormsComponent } from './test-forms.component';

describe('BaseFormsComponent', () => {
  let component: TestFormsComponent;
  let fixture: ComponentFixture<TestFormsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestFormsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestFormsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
