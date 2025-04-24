import { Injectable } from '@angular/core';
import { Stack } from '@processpuzzle/util';
import { NavigationEnd, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class NavigateBackService {
  private readonly routeHistory = new Stack<string>();

  constructor(private readonly router: Router) {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.addRouteToStack(event.urlAfterRedirects); // Add the current route to the stack
      }
    });
  }

  // region public accessor methods
  public goBack(): void {
    if (this.routeHistory.size() > 1) {
      this.routeHistory.pop(); // Remove current route
      const previousRoute = this.routeHistory.pop(); // Get the previous route
      if (previousRoute) {
        this.router.navigateByUrl(previousRoute); // Navigate to the previous route
      }
    } else {
      console.log('No previous routes to navigate back to.');
    }
  }

  public getRouteStack(): Stack<string> {
    return this.routeHistory;
  }

  public clearHistory(): void {
    this.routeHistory.clear();
  }

  // endregion

  // protected, private helper methods
  private addRouteToStack(route: string): void {
    if (this.routeHistory.size() === 0 || this.routeHistory.peek() !== route) {
      this.routeHistory.push(route);
    }
  }

  // endregion
}
