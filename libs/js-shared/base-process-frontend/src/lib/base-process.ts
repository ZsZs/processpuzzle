export class BaseProcess {
  constructor(
    readonly name: string,
    readonly steps: readonly string[] = [],
  ) {}

  stepCount(): number {
    return this.steps.length;
  }
}
