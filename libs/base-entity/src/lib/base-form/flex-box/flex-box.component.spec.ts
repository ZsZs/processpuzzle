import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlexBoxComponent } from './flex-box.component';
import { TestEntity } from '../../test-entity';

describe('FlexControlComponent', () => {
  let component: FlexBoxComponent<TestEntity>;
  let fixture: ComponentFixture<FlexBoxComponent<TestEntity>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlexBoxComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(FlexBoxComponent<TestEntity>);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
