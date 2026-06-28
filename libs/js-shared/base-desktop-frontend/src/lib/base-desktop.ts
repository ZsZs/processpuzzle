export class BaseDesktop {
  constructor(
    readonly title: string,
    readonly panels: readonly string[] = [],
  ) {}

  panelCount(): number {
    return this.panels.length;
  }
}
