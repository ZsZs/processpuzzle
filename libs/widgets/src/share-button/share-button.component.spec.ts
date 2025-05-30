import { fireEvent, screen } from '@testing-library/angular';
import { ShareButtonComponent } from './share-button.component';
import { MatIcon } from '@angular/material/icon';
import { OverlayModule } from '@angular/cdk/overlay';
import { Component } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShareButtons } from 'ngx-sharebuttons/buttons';
import { By } from '@angular/platform-browser';

const toggleOverlay = (fixture: ComponentFixture<ShareButtonComponent>) => {
  const button = fixture.debugElement.query(By.css('button[aria-label="Share Button"]')).nativeElement;
  fireEvent.click(button);
  fixture.detectChanges();
};

@Component({
  selector: 'share-buttons',
  template: `<p>Mock Share Buttons</p>`,
})
class MockShareButtonsComponent {}

describe('ShareButtonComponent', () => {
  let component: ShareButtonComponent;
  let fixture: ComponentFixture<ShareButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShareButtonComponent, MatIcon, MatIconButton, OverlayModule],
    })
      .overrideComponent(ShareButtonComponent, {
        remove: { imports: [ShareButtons] },
        add: { imports: [MockShareButtonsComponent] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(ShareButtonComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', async () => {
    expect(component).toBeTruthy(); // Ensures the ShareButtonComponent is rendered
    expect(screen.getByRole('button', { name: /Share Button/i })).toBeTruthy(); // Ensures the button exists
  });

  // Test to simulate the overlay opening
  it('should open the overlay and display mock ShareButtons', async () => {
    toggleOverlay(fixture);

    // Ensure that the mock elements inside the overlay are visible
    expect(screen.getByText(/Mock Share Buttons/i)).toBeTruthy();
  });

  // Test to ensure the overlay closes when clicking outside
  it('should close the overlay when clicking outside', async () => {
    toggleOverlay(fixture);

    expect(screen.getByText(/Mock Share Buttons/i)).toBeTruthy(); // the overlay is open

    const backdrop = document.querySelector('.cdk-overlay-backdrop');
    backdrop?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(screen.queryByText(/Mock Share Buttons/i)).toBeFalsy(); // the overlay is closed
  });
});
