package com.processpuzzle.document.usecase;

import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

/**
 * Builds a {@link DocumentGuard} around a chosen policy, so a test can pin what the principal may
 * do without standing up a Spring context.
 *
 * <p>The guard takes an {@link ObjectProvider} because in production it falls back to permit-all
 * when the deploying application supplies no policy bean. Only {@code getIfUnique} is implemented
 * here — the other methods throw, so a future change that starts calling one of them fails loudly
 * in tests instead of silently taking a different path than production does.
 */
final class TestGuards {

    private TestGuards() {
    }

    /** A guard with no policy bean at all — exercises the production permit-all fallback. */
    static DocumentGuard permitAll() {
        return new DocumentGuard(new SingletonProvider(null));
    }

    static DocumentGuard with(DocumentAccessPolicy policy) {
        return new DocumentGuard(new SingletonProvider(policy));
    }

    private record SingletonProvider(DocumentAccessPolicy policy) implements ObjectProvider<DocumentAccessPolicy> {

        @Override
        public DocumentAccessPolicy getIfUnique(Supplier<DocumentAccessPolicy> defaultSupplier) {
            return policy == null ? defaultSupplier.get() : policy;
        }

        @Override
        public DocumentAccessPolicy getObject(Object... args) {
            throw new UnsupportedOperationException("Not used by DocumentGuard");
        }

        @Override
        public DocumentAccessPolicy getObject() {
            throw new UnsupportedOperationException("Not used by DocumentGuard");
        }

        @Override
        public DocumentAccessPolicy getIfAvailable() {
            throw new UnsupportedOperationException("Not used by DocumentGuard");
        }

        @Override
        public DocumentAccessPolicy getIfUnique() {
            throw new UnsupportedOperationException("Not used by DocumentGuard");
        }
    }
}
