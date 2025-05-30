import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { NavigateBackService } from './navigate-back.service';
import { Subject } from 'rxjs';

describe('NavigateBackService', () => {
  let navigateBackService: NavigateBackService;
  let router: jest.Mocked<Router>;
  let routerEvents$: Subject<unknown>;

  beforeEach(() => {
    // Mock the Angular Router
    routerEvents$ = new Subject<unknown>();
    router = {
      events: routerEvents$.asObservable(),
      navigateByUrl: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    // TestBed Configuration
    TestBed.configureTestingModule({
      providers: [NavigateBackService, { provide: Router, useValue: router }],
    });

    navigateBackService = TestBed.inject(NavigateBackService);
  });

  afterEach(() => {
    routerEvents$.complete(); // Clean up the event subject
    jest.clearAllMocks(); // Clear mocks between tests
  });

  it('should initialize with an empty route history', () => {
    const routeHistory = navigateBackService.getRouteStack();
    expect(routeHistory.isEmpty()).toBe(true); // Verify initial state is empty
  });

  it('should add routes to the history on NavigationEnd events', () => {
    // Emit NavigationEnd Events
    routerEvents$.next(new NavigationEnd(1, '/route1', '/route1'));
    routerEvents$.next(new NavigationEnd(2, '/route2', '/route2'));

    const routeHistory = navigateBackService.getRouteStack();
    expect(routeHistory.size()).toBe(2); // Two routes in history
    expect(routeHistory.peek()).toBe('/route2'); // Last route should be '/route2'
  });

  it('should ignore duplicate consecutive routes in the history', () => {
    // Emit NavigationEnd Events with duplicates
    routerEvents$.next(new NavigationEnd(1, '/route1', '/route1'));
    routerEvents$.next(new NavigationEnd(2, '/route1', '/route1')); // Duplication
    routerEvents$.next(new NavigationEnd(3, '/route2', '/route2'));

    const routeHistory = navigateBackService.getRouteStack();
    expect(routeHistory.size()).toBe(2); // Only unique routes are stored
    expect(routeHistory.peek()).toBe('/route2');
  });

  it('should navigate to the previous route when goBack is called', () => {
    // Simulate route navigation
    routerEvents$.next(new NavigationEnd(1, '/route1', '/route1'));
    routerEvents$.next(new NavigationEnd(2, '/route2', '/route2'));
    routerEvents$.next(new NavigationEnd(3, '/route3', '/route3'));

    // Call goBack
    navigateBackService.goBack();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/route2'); // Should go back to '/route2'

    const routeHistory = navigateBackService.getRouteStack();
    expect(routeHistory.peek()).toBe('/route1'); // Current top route in stack should be '/route1'
  });

  it('should warn if goBack is called with less than two routes', () => {
    const consoleWarnSpy = jest.spyOn(console, 'log').mockImplementation();

    // Emit a single route
    routerEvents$.next(new NavigationEnd(1, '/route1', '/route1'));

    navigateBackService.goBack(); // Attempt to go back with insufficient routes

    expect(consoleWarnSpy).toHaveBeenCalledWith('No previous routes to navigate back to.'); // Correct warning should be issued
    expect(router.navigateByUrl).not.toHaveBeenCalled(); // No navigation should occur
  });

  it('should clear the history when clearHistory is called', () => {
    // Simulate route navigation
    routerEvents$.next(new NavigationEnd(1, '/route1', '/route1'));
    routerEvents$.next(new NavigationEnd(2, '/route2', '/route2'));

    // Clear the history
    navigateBackService.clearHistory();

    const routeHistory = navigateBackService.getRouteStack();
    expect(routeHistory.isEmpty()).toBe(true); // History should be empty after clearing
  });
});
