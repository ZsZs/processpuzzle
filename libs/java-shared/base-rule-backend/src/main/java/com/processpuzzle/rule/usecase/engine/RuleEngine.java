package com.processpuzzle.rule.usecase.engine;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sandboxed evaluator for ProcessPuzzle Constraint Language (PPCL) expressions, backed by
 * GraalJS. A PPCL rule is a single JS expression evaluated against one entity, e.g.:
 *
 * <pre>{@code entity.lineItems.every(li => li.quantity > 0)}</pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>One shared {@link Engine}</b> for the whole application. GraalVM caches compiled
 *       code at the Engine level, so re-evaluating the same {@link Source} in different
 *       Contexts is cheap even though each Context gets its own {@link Value}.</li>
 *   <li><b>One {@link Context} per thread.</b> Context is not thread-safe — only one thread
 *       may execute in a given Context at a time. A ThreadLocal Context (one per request
 *       thread in a typical Spring Boot deployment) avoids contention without needing a
 *       full pooling library for this PoC.</li>
 *   <li><b>{@link Source} objects are cached centrally and re-evaluated per-thread.</b> The
 *       compiled {@link Value} produced by evaluating a rule's Source cannot be shared
 *       across Contexts/threads, but the Source itself is immutable and cheap to re-eval
 *       thanks to the shared Engine's code cache — so each thread lazily compiles its own
 *       Value the first time it needs a given rule, and reuses it after that.</li>
 *   <li><b>HostAccess.NONE / IOAccess.NONE</b> mean rule expressions have no path to real
 *       Java objects, the filesystem, threads, or processes — only pure JS computation over
 *       whatever {@link EntityProxy} explicitly exposes.</li>
 *   <li><b>A statement limit</b> defends against accidental or malicious runaway expressions
 *       (e.g. an unbounded loop), independent of the read-only/no-host-access sandboxing.</li>
 * </ul>
 *
 */
@Service
public class RuleEngine implements AutoCloseable {
    private final Engine engine;
    private final Map<String, Source> ruleSources = new ConcurrentHashMap<>();
    private final ThreadLocal<ThreadContext> threadContext;

    public RuleEngine() {
        this.engine = Engine.newBuilder("js")
                // Expected on a stock JDK (no Graal JIT compiler installed) — see README.
                // Rule expressions are short and infrequent, so interpreted-mode is fine for
                // this PoC; revisit only if benchmarking shows evaluation is a bottleneck.
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        this.threadContext = ThreadLocal.withInitial(this::newThreadContext);
    }

    private ThreadContext newThreadContext() {
        Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(50_000, null)
                        .build())
                .build();
        return new ThreadContext(context);
    }

    /**
     * Registers (and compiles a cacheable Source for) a named rule. The expression is
     * wrapped as a single-argument function, {@code (entity) => (<expression>)}, so it can
     * be compiled once and executed many times against different entity instances.
     */
    public void registerRule(String ruleName, String expression) {
        // Strict mode ensures that failed writes to the read-only EntityProxy surface as a
        // catchable TypeError instead of silently no-op'ing (sloppy-mode [[Set]] behavior).
        String wrapped = "(entity) => { 'use strict'; return (" + expression + "); }";
        Source source = Source.newBuilder("js", wrapped, ruleName + ".js")
                .buildLiteral();
        ruleSources.put(ruleName, source);
    }

    /**
     * Evaluates a previously-registered rule against the given entity data. Expects the
     * rule expression to resolve to a boolean.
     */
    public boolean evaluate(String ruleName, Map<String, Object> entityData) {
        Source source = ruleSources.get(ruleName);
        if (source == null) {
            throw new IllegalArgumentException("Rule not registered: " + ruleName);
        }
        ThreadContext tc = threadContext.get();
        Value ruleFn = tc.ruleFunctions.computeIfAbsent(ruleName, k -> tc.context.eval(source));
        Value result = ruleFn.execute(EntityProxy.wrap(entityData));
        return result.asBoolean();
    }

    /**
     * Removes a rule so it can no longer be evaluated. Used when a persisted
     * RuleDefinition is deleted, or disabled, via the CRUD API.
     *
     * <p>Note: a thread that already lazily compiled this rule's Value keeps its own copy
     * in {@code ThreadContext.ruleFunctions} until that thread happens to evict/recreate its
     * context — harmless (no new compilations happen, and the Source is gone from
     * {@code ruleSources}), but worth knowing about if you need an absolute guarantee that
     * an unregistered rule can never execute again on any thread.
     */
    public void unregisterRule(String ruleName) {
        ruleSources.remove(ruleName);
    }

    public boolean isRegistered(String ruleName) {
        return ruleSources.containsKey(ruleName);
    }

    @Override
    public void close() {
        // Note: in a real multi-threaded deployment you'd need to track and close every
        // thread's Context, not just the calling thread's. A request-scoped or pooled
        // Context strategy (rather than raw ThreadLocal) makes lifecycle cleanup easier —
        // worth revisiting once this moves past PoC stage.
        threadContext.get().context.close();
        engine.close();
    }

    private static final class ThreadContext {
        final Context context;
        final Map<String, Value> ruleFunctions = new ConcurrentHashMap<>();

        ThreadContext(Context context) {
            this.context = context;
        }
    }
}
