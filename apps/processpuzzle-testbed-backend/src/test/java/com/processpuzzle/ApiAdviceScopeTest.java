package com.processpuzzle;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two halves of the {@link ApiAdviceOrder} convention across the whole application. Like
 * {@link ModularityTests}, this belongs here because the application build is the only one with every
 * library on the classpath — a library's own test sees one advice and can prove nothing about ties.
 *
 * <p>Spring answers with the <em>first</em> advice that has any handler matching the exception. Two
 * advices on the same rung whose scopes overlap are therefore a coin toss decided by bean ordering,
 * and the types most likely to be claimed twice are the ones no feature owns — Spring's
 * {@code OptimisticLockingFailureException}, {@code IllegalArgumentException}. That is not
 * theoretical: three advices claimed optimistic-lock failures globally, so a stale write on a
 * workflow row came back as {@code document.stale-write}.
 */
class ApiAdviceScopeTest {

    private static final String ROOT = "com.processpuzzle";

    /**
     * The two advices that are deliberately global: a generic refusal and an unhandled failure mean
     * the same thing whichever module raised them.
     */
    private static final Set<String> INTENTIONALLY_GLOBAL = Set.of(
            "com.processpuzzle.core.exception.ApiExceptionHandler",
            "com.processpuzzle.core.exception.UnhandledExceptionHandler");

    @Test
    void everyFeatureAdviceIsScopedToItsOwnModule() {
        List<Class<?>> unscoped = advices().stream()
                .filter(advice -> !INTENTIONALLY_GLOBAL.contains(advice.getName()))
                .filter(advice -> scopeOf(advice).isEmpty())
                .toList();

        assertThat(unscoped)
                .withFailMessage("""
                        These @RestControllerAdvice classes are global, so they answer for every module's \
                        endpoints — including for exception types a sibling advice also claims, which makes \
                        the winner depend on bean ordering. Declare the module's own package, e.g. \
                        @RestControllerAdvice(basePackages = "com.processpuzzle.workflow"). See ApiAdviceOrder.
                        Offenders: %s""", names(unscoped))
                .isEmpty();
    }

    @Test
    void everyAdviceDeclaresItsRung() {
        List<Class<?>> unordered = advices().stream()
                .filter(advice -> AnnotationUtils.findAnnotation(advice, Order.class) == null)
                .toList();

        assertThat(unordered)
                .withFailMessage("""
                        These @RestControllerAdvice classes declare no @Order, so they sit at \
                        LOWEST_PRECEDENCE alongside the catch-all and lose to it arbitrarily. Pick a rung \
                        from ApiAdviceOrder. Offenders: %s""", names(unordered))
                .isEmpty();
    }

    /**
     * The invariant the other two exist to make true. A pair is ambiguous when it shares a rung
     * <em>and</em> its scopes overlap — a global advice overlaps everything, and two scoped advices
     * overlap only if one's package contains the other's.
     */
    @Test
    void noTwoAdvicesOnOneRungClaimTheSameExceptionWithinOverlappingScopes() {
        List<Class<?>> advices = advices();
        List<String> ambiguities = new ArrayList<>();

        for (int i = 0; i < advices.size(); i++) {
            for (int j = i + 1; j < advices.size(); j++) {
                Class<?> left = advices.get(i);
                Class<?> right = advices.get(j);
                if (rungOf(left) != rungOf(right) || !scopesOverlap(left, right)) {
                    continue;
                }
                Set<String> shared = new TreeSet<>(claimedExceptions(left));
                shared.retainAll(claimedExceptions(right));
                if (!shared.isEmpty()) {
                    ambiguities.add("%s vs %s on rung %d, both claiming %s"
                            .formatted(left.getSimpleName(), right.getSimpleName(), rungOf(left), shared));
                }
            }
        }

        assertThat(ambiguities)
                .withFailMessage("""
                        Ambiguous exception handling: each pair below shares an @Order rung and an \
                        overlapping scope, so which one answers is decided by bean ordering rather than by \
                        anything in the code. Either scope them apart or let one of them own the type.
                        %s""", String.join("\n", ambiguities))
                .isEmpty();
    }

    /** The regression this whole class was written for, asserted by name so the failure reads plainly. */
    @Test
    void optimisticLockFailuresAreClaimedByAtMostOneAdvicePerModule() {
        Map<String, List<String>> claimantsByScope = new LinkedHashMap<>();
        for (Class<?> advice : advices()) {
            boolean claimsLockFailure = claimedExceptions(advice).stream()
                    .anyMatch(name -> name.endsWith("OptimisticLockingFailureException"));
            if (claimsLockFailure) {
                claimantsByScope
                        .computeIfAbsent(scopeOf(advice).isEmpty() ? "<global>" : String.join(",", scopeOf(advice)),
                                key -> new ArrayList<>())
                        .add(advice.getSimpleName());
            }
        }

        assertThat(claimantsByScope)
                .withFailMessage("""
                        More than one advice claims an optimistic-lock failure within one scope, or one \
                        claims it globally. A stale write must be reported by the module whose row went \
                        stale. Claimants by scope: %s""", claimantsByScope)
                .allSatisfy((scope, claimants) -> {
                    assertThat(scope).isNotEqualTo("<global>");
                    assertThat(claimants).hasSize(1);
                });
    }

    // ---------------------------------------------------------------- helpers

    private List<Class<?>> advices() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestControllerAdvice.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT)) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned advice cannot be loaded: " + definition.getBeanClassName(), e);
            }
        }
        assertThat(found).as("advice scan found nothing — the scanner or the root package is wrong").isNotEmpty();
        return found;
    }

    /** The packages an advice restricts itself to; empty means global. */
    private List<String> scopeOf(Class<?> advice) {
        RestControllerAdvice annotation = AnnotationUtils.findAnnotation(advice, RestControllerAdvice.class);
        if (annotation == null) {
            return List.of();
        }
        List<String> packages = new ArrayList<>(List.of(annotation.basePackages()));
        for (Class<?> marker : annotation.basePackageClasses()) {
            packages.add(marker.getPackageName());
        }
        for (Class<?> assignable : annotation.assignableTypes()) {
            packages.add(assignable.getName());
        }
        return packages;
    }

    private boolean scopesOverlap(Class<?> left, Class<?> right) {
        List<String> leftScope = scopeOf(left);
        List<String> rightScope = scopeOf(right);
        if (leftScope.isEmpty() || rightScope.isEmpty()) {
            return true;
        }
        return leftScope.stream().anyMatch(l -> rightScope.stream().anyMatch(r -> l.startsWith(r) || r.startsWith(l)));
    }

    private int rungOf(Class<?> advice) {
        Order order = AnnotationUtils.findAnnotation(advice, Order.class);
        return order == null ? org.springframework.core.Ordered.LOWEST_PRECEDENCE : order.value();
    }

    private Set<String> claimedExceptions(Class<?> advice) {
        Set<String> claimed = new LinkedHashSet<>();
        for (Method method : advice.getDeclaredMethods()) {
            ExceptionHandler handler = AnnotationUtils.findAnnotation(method, ExceptionHandler.class);
            if (handler == null) {
                continue;
            }
            for (Class<? extends Throwable> type : handler.value()) {
                claimed.add(type.getName());
            }
        }
        return claimed;
    }

    private String names(List<Class<?>> classes) {
        return classes.stream().map(Class::getName).sorted().toList().toString();
    }
}
