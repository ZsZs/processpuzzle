import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { NgxLoggerLevel, provideLogger } from 'ngx-logging-kit';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { TestEntity } from '../../test-entity';
import { ComponentRefComponent } from './component-ref.component';

const LOGGING_CONFIGURATION = {
  level: NgxLoggerLevel.OFF,
  serverLogLevel: NgxLoggerLevel.OFF,
  disableConsoleLogging: true,
};

async function setupRef({ disabled = false, displayName = 'component_1', linkedEntityType = 'TestEntityComponent' } = {}) {
  await TestBed.configureTestingModule({
    imports: [ComponentRefComponent],
    providers: [provideRouter([]), provideLogger(LOGGING_CONFIGURATION)],
  }).compileComponents();

  const fixture = TestBed.createComponent(ComponentRefComponent<TestEntity>);
  fixture.componentRef.setInput('componentEntity', new TestEntity('child-1', displayName));
  fixture.componentRef.setInput('displayName', displayName);
  fixture.componentRef.setInput('disabled', disabled);
  fixture.componentRef.setInput('linkedEntityType', linkedEntityType);
  fixture.detectChanges();

  return { fixture, component: fixture.componentInstance };
}

describe('ComponentRefComponent', () => {
  describe('navigateToComponent()', () => {
    it('prevents default and forwards the component id to the form navigator', async () => {
      const { component } = await setupRef();
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/parents/parent-1/details');
      vi.spyOn(formNavigator, 'navigateToRelated').mockResolvedValue(undefined);
      const preventDefault = vi.fn();

      component.navigateToComponent({ preventDefault } as unknown as Event);

      expect(preventDefault).toHaveBeenCalled();
      expect(formNavigator.navigateToRelated).toHaveBeenCalledWith('TestEntityComponent', 'child-1', '/parents/parent-1/details');
    });
  });

  describe('requestDelete()', () => {
    it('asks the list to delete the component', async () => {
      const { component } = await setupRef();
      const deleteRequested = vi.fn();
      component.deleteRequested.subscribe(deleteRequested);

      component.requestDelete();

      expect(deleteRequested).toHaveBeenCalled();
    });

    it('stays silent when disabled', async () => {
      const { component } = await setupRef({ disabled: true });
      const deleteRequested = vi.fn();
      component.deleteRequested.subscribe(deleteRequested);

      component.requestDelete();

      expect(deleteRequested).not.toHaveBeenCalled();
    });
  });

  describe('template', () => {
    it('renders the display name and the delete button', async () => {
      const { fixture } = await setupRef();
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('a')?.textContent?.trim()).toBe('component_1');
      expect(host.querySelector('button[aria-label="Delete component"]')).not.toBeNull();
    });

    it('hides the delete button when disabled', async () => {
      const { fixture } = await setupRef({ disabled: true });

      expect((fixture.nativeElement as HTMLElement).querySelector('button[aria-label="Delete component"]')).toBeNull();
    });
  });
});
