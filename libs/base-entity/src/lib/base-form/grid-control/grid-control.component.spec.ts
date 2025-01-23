import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GridControlComponent } from './grid-control.component';

describe('FlexControlComponent', () => {
  let component: GridControlComponent;
  let fixture: ComponentFixture<GridControlComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GridControlComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(GridControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
