import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HeaderComponent } from './header.component';
import { By } from '@angular/platform-browser';
import { Component } from '@angular/core';
import { LikeButtonComponent, ShareButtonComponent } from '@processpuzzle/widgets';

@Component({
  selector: 'pp-like-button',
  template: `<p>Mock Like Button</p>`,
})
class MockLikeButtonComponent {}

@Component({
  selector: 'pp-share-button',
  template: `<p>Mock Share Button</p>`,
})
class MockShareButtonComponent {}

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [],
    })
      .overrideComponent(HeaderComponent, {
        remove: { imports: [LikeButtonComponent, ShareButtonComponent] },
        add: { imports: [MockLikeButtonComponent, MockShareButtonComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it.skip('template structure contains: mat-toolbar:', () => {
    const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
    expect(matToolbar).toBeTruthy();
  });
});
