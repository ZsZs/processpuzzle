export class BaseState {
  constructor(
    readonly name: string,
    readonly isFinal: boolean = false,
  ) {}

  describe(): string {
    return this.isFinal ? `${this.name} (final)` : this.name;
  }
}
