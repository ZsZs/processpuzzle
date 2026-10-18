import { ComponentPortal } from '@angular/cdk/portal';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SidenavContentService } from './sidenav-content.service';

@Component({ template: '' })
class ContentComponent {}

describe('SidenavContentService', () => {
  let service: SidenavContentService;

  beforeEach(() => {
    service = TestBed.inject(SidenavContentService);
  });

  it('is inactive with nothing attached', () => {
    expect(service.portal()).toBeUndefined();
    expect(service.title()).toBe('');
    expect(service.active()).toBe(false);
  });

  it('shows attached content first and toggles to the menu and back', () => {
    service.attach(new ComponentPortal(ContentComponent), 'Handbook');

    expect(service.active()).toBe(true);
    expect(service.title()).toBe('Handbook');

    service.toggle();
    expect(service.mode()).toBe('menu');
    expect(service.active()).toBe(false);

    service.toggle();
    expect(service.active()).toBe(true);
  });

  it('detaches what it attached and resets the mode', () => {
    const detach = service.attach(new ComponentPortal(ContentComponent), 'Handbook');
    service.toggle();

    detach();

    expect(service.portal()).toBeUndefined();
    expect(service.mode()).toBe('content');
  });

  it('ignores a stale detach once another page has attached its own content', () => {
    const detachFirst = service.attach(new ComponentPortal(ContentComponent), 'First');
    const second = new ComponentPortal(ContentComponent);
    service.attach(second, 'Second');

    detachFirst();

    expect(service.portal()).toBe(second);
    expect(service.title()).toBe('Second');
  });

  it('counts the outlets that can show attached content', () => {
    const first = service.registerOutlet();
    const second = service.registerOutlet();
    expect(service.hasOutlet()).toBe(true);

    first();
    first();
    expect(service.hasOutlet()).toBe(true);

    second();
    expect(service.hasOutlet()).toBe(false);
  });
});
