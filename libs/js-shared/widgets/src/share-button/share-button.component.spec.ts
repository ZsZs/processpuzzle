import { fireEvent, screen } from '@testing-library/angular';
import { ShareButtonComponent } from './share-button.component';
import { MatIcon } from '@angular/material/icon';
import { OverlayContainer, OverlayModule } from '@angular/cdk/overlay';
import { Component } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShareButtons } from 'ngx-sharebuttons/buttons';
import { By } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';

@Component({
  selector: 'share-buttons',
  template: `<p>Mock Share Buttons</p>`,
})
class MockShareButtonsComponent {}

describe('ShareButtonComponent', () => {
  let component: ShareButtonComponent;
  let fixture: ComponentFixture<ShareButtonComponent>;
  let overlayContainer: OverlayContainer;

  const getTriggerButton = (): HTMLButtonElement =>
    fixture.debugElement.query(By.css('button[aria-label="Share Button"]')).nativeElement as HTMLButtonElement;

  const clickTriggerButton = () => {
    fireEvent.click(getTriggerButton());
    fixture.detectChanges();
  };

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
    overlayContainer = TestBed.inject(OverlayContainer);
    fixture.detectChanges();
  });

  // region Rendering
  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render the trigger button with mat-icon "share"', () => {
    const button = getTriggerButton();
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Share Button');

    const icon = fixture.debugElement.query(By.css('mat-icon')).nativeElement as HTMLElement;
    expect(icon.textContent?.trim()).toBe('share');
  });

  it('should start with the overlay closed', () => {
    expect(component.isOpen).toBe(false);
    expect(screen.queryByText(/Mock Share Buttons/i)).toBeFalsy();
  });
  // endregion

  // region Template listener coverage (button click → onShare, backdrop click → onClose)
  it('should open the overlay when the trigger button is clicked', () => {
    clickTriggerButton();

    expect(component.isOpen).toBe(true);
    expect(screen.getByText(/Mock Share Buttons/i)).toBeTruthy();
    expect(overlayContainer.getContainerElement().querySelector('.share-buttons-container')).toBeTruthy();
  });

  it('should toggle the overlay closed when the trigger button is clicked a second time', () => {
    clickTriggerButton();
    expect(component.isOpen).toBe(true);

    clickTriggerButton();

    expect(component.isOpen).toBe(false);
    expect(screen.queryByText(/Mock Share Buttons/i)).toBeFalsy();
  });

  it('should close the overlay when the backdrop is clicked', () => {
    clickTriggerButton();
    expect(component.isOpen).toBe(true);

    const backdrop = overlayContainer.getContainerElement().querySelector('.cdk-overlay-backdrop') as HTMLElement;
    expect(backdrop).toBeTruthy();
    backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(component.isOpen).toBe(false);
    expect(screen.queryByText(/Mock Share Buttons/i)).toBeFalsy();
  });
  // endregion

  // region Direct method coverage
  it('onShare() should toggle isOpen', () => {
    expect(component.isOpen).toBe(false);

    component.onShare();
    expect(component.isOpen).toBe(true);

    component.onShare();
    expect(component.isOpen).toBe(false);
  });

  it('onClose() should set isOpen to false regardless of prior state', () => {
    component.onClose();
    expect(component.isOpen).toBe(false);

    component.onShare();
    expect(component.isOpen).toBe(true);
    component.onClose();
    expect(component.isOpen).toBe(false);
  });
  // endregion
});
