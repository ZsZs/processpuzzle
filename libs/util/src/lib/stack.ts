export class Stack<T> {
  private items: T[] = []; // Array zur Speicherung der Elemente

  // Element zum Stack hinzufügen
  push(element: T): void {
    this.items.push(element);
  }

  // Element vom Stack entfernen
  pop(): T | undefined {
    if (this.isEmpty()) {
      return undefined; // Stack ist leer
    }
    return this.items.pop();
  }

  // Zeige das oberste Element ohne es zu entfernen
  peek(): T | undefined {
    if (this.isEmpty()) {
      return undefined; // Stack ist leer
    }
    return this.items[this.items.length - 1];
  }

  // Prüfen, ob der Stack leer ist
  isEmpty(): boolean {
    return this.items.length === 0;
  }

  // Anzahl der Elemente im Stack
  size(): number {
    return this.items.length;
  }

  // Stack leeren
  clear(): void {
    this.items = [];
  }
}
