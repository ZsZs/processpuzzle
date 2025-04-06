import { Stack } from './stack'; // Import the Stack class

describe('Stack', () => {
  let stack: Stack<number>;

  beforeEach(() => {
    stack = new Stack<number>(); // Create a new stack before each test
  });

  test('should initialize as an empty stack', () => {
    expect(stack.isEmpty()).toBe(true);
    expect(stack.size()).toBe(0);
  });

  test('should allow pushing elements onto the stack', () => {
    stack.push(10);
    stack.push(20);

    expect(stack.isEmpty()).toBe(false);
    expect(stack.size()).toBe(2);
    expect(stack.peek()).toBe(20); // The last pushed item should be on top
  });

  test('should pop elements off the stack', () => {
    stack.push(10);
    stack.push(20);
    stack.push(30);

    const popped = stack.pop(); // Remove the top element
    expect(popped).toBe(30); // Should return the last pushed item
    expect(stack.size()).toBe(2);
    expect(stack.peek()).toBe(20); // The next item on top after popping
  });

  test('should return undefined when popping from an empty stack', () => {
    const popped = stack.pop();
    expect(popped).toBeUndefined(); // Should return undefined
    expect(stack.isEmpty()).toBe(true);
  });

  test('should peek the top element without removing it', () => {
    stack.push(10);
    stack.push(20);

    const peeked = stack.peek();
    expect(peeked).toBe(20); // The top element should be returned
    expect(stack.size()).toBe(2); // The size should remain the same
  });

  test('should return undefined when peeking an empty stack', () => {
    const peeked = stack.peek();
    expect(peeked).toBeUndefined(); // Should return undefined
  });

  test('should correctly report if the stack is empty', () => {
    expect(stack.isEmpty()).toBe(true);

    stack.push(10);
    expect(stack.isEmpty()).toBe(false);

    stack.pop();
    expect(stack.isEmpty()).toBe(true);
  });

  test('should clear all elements from the stack', () => {
    stack.push(10);
    stack.push(20);
    stack.push(30);
    expect(stack.size()).toBe(3);

    stack.clear();
    expect(stack.isEmpty()).toBe(true);
    expect(stack.size()).toBe(0);
    expect(stack.peek()).toBeUndefined();
  });
});
