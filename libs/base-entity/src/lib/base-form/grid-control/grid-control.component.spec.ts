import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GridControlComponent } from './grid-control.component';
import { TestEntity } from '../../test-entity';

describe('FlexControlComponent', () => {
  let component: GridControlComponent<TestEntity>;
  let fixture: ComponentFixture<GridControlComponent<TestEntity>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GridControlComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(GridControlComponent<TestEntity>);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
