import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UtilsComponent } from './util.component';

describe('UtilsComponent', () => {
  let component: UtilsComponent;
  let fixture: ComponentFixture<UtilsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UtilsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(UtilsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
