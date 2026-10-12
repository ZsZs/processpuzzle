// Load shapes from the strategy's test phases. Each returns a k6 `scenarios` entry.
const hold = __ENV.STEP_HOLD || '5m';

// User steps: 10, 50, 100, 250, 500, 1000 VUs, each held. Result: resources per user.
export function userSteps(exec, steps = [10, 50, 100, 250, 500, 1000]) {
  return { executor: 'ramping-vus', exec, startVUs: 0, gracefulRampDown: '30s',
    stages: steps.flatMap((t) => [{ duration: '1m', target: t }, { duration: hold, target: t }]) };
}
// Stress: climb until a threshold aborts the run (abortOnFail in lib/config.js).
export function stress(exec, maxVus = 3000) {
  return { executor: 'ramping-vus', exec, startVUs: 0, gracefulRampDown: '30s',
    stages: [{ duration: '20m', target: maxVus }] };
}
// Spike: 10x jump, then back, to see degradation and recovery.
export function spike(exec, base = 50) {
  return { executor: 'ramping-vus', exec, startVUs: base, gracefulRampDown: '30s',
    stages: [{ duration: '5m', target: base }, { duration: '30s', target: base * 10 },
             { duration: '5m', target: base * 10 }, { duration: '30s', target: base }, { duration: '10m', target: base }] };
}
// Soak: constant ~70% of the capacity found, 4-8 h.
export function soak(exec, vus, duration = '6h') {
  return { executor: 'constant-vus', exec, vus, duration, gracefulStop: '1m' };
}
