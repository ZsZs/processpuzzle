export class BaseRule {
  constructor(
    readonly name: string,
    readonly priority = 0,
  ) {}

  describe(): string {
    return `${this.name} (priority ${this.priority})`;
  }
}
