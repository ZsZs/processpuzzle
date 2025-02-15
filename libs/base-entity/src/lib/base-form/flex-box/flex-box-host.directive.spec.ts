import { Component, ViewChild } from '@angular/core';
import { FlexBoxHostDirective } from './flex-box-host.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';

@Component({
  selector: 'mock-component',
  imports: [FlexBoxHostDirective],
  template: `
    <div class="flex-box-container">
      <ng-template flexBoxHost></ng-template>
    </div>
  `,
})
class DirectiveMockComponent {
  @ViewChild(FlexBoxHostDirective, { static: true, read: FlexBoxHostDirective }) flexBoxHost!: FlexBoxHostDirective;
}

describe('FlexBoxHostDirective', () => {
  let fixture: ComponentFixture<DirectiveMockComponent>;
  beforeEach(() => {
    fixture = TestBed.configureTestingModule({
      imports: [DirectiveMockComponent],
    }).createComponent(DirectiveMockComponent);
    fixture.detectChanges();
  });

  it('should create an instance', async () => {
    expect(fixture).toBeTruthy();
    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.componentInstance.flexBoxHost).toBeTruthy();
  });
});
