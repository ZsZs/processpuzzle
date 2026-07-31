package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.usecase.service.ReservedOrganizationKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Answers whether an organization key can be claimed, for live feedback on the sign-up form.
 *
 * <p>Validates the slug itself rather than relying on bean validation of the query parameter: a
 * form reports as the user types, so partial input ({@code "m"}, {@code "My Org"}) has to come back
 * as a structured "not available, here is why" rather than a bare 400.
 */
@Service
@Transactional(readOnly = true)
public class CheckOrganizationKey {

    /** Mirrors the {@code OrganizationKey} pattern in shared-api.yaml. */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 63;
    private static final int MAX_SUGGESTIONS = 3;

    private final OrganizationRepository repository;
    private final ReservedOrganizationKeys reservedKeys;

    public CheckOrganizationKey(OrganizationRepository repository, ReservedOrganizationKeys reservedKeys) {
        this.repository = repository;
        this.reservedKeys = reservedKeys;
    }

    public KeyCheckOutcome execute(String key) {
        String candidate = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);

        if (candidate.isEmpty()) {
            return KeyCheckOutcome.unavailable(candidate, "organization.key.missing", List.of());
        }
        if (candidate.length() < MIN_LENGTH || candidate.length() > MAX_LENGTH) {
            return KeyCheckOutcome.unavailable(candidate, "organization.key.length", List.of());
        }
        if (!KEY_PATTERN.matcher(candidate).matches()) {
            return KeyCheckOutcome.unavailable(candidate, "organization.key.invalid", suggestionsFor(candidate));
        }
        if (reservedKeys.isReserved(candidate)) {
            return KeyCheckOutcome.unavailable(candidate, "organization.key.reserved", suggestionsFor(candidate));
        }
        if (repository.existsById(candidate)) {
            return KeyCheckOutcome.unavailable(candidate, "organization.key.taken", suggestionsFor(candidate));
        }
        return KeyCheckOutcome.available(candidate);
    }

    /** Whether {@code key} is claimable, without building suggestions. Used by provisioning. */
    public boolean isClaimable(String key) {
        return execute(key).available();
    }

    /** The reason a key cannot be claimed, or {@code null} when it can. */
    public String rejectionReason(String key) {
        return execute(key).errorId();
    }

    private List<String> suggestionsFor(String candidate) {
        String base = slugify(candidate);
        if (base.isEmpty()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(base);
        candidates.add(base + "-app");
        for (int i = 1; candidates.size() < MAX_SUGGESTIONS + 3; i++) {
            candidates.add(base + "-" + i);
        }
        return candidates.stream()
                .filter(suggestion -> suggestion.length() >= MIN_LENGTH && suggestion.length() <= MAX_LENGTH)
                .filter(suggestion -> !reservedKeys.isReserved(suggestion))
                .filter(suggestion -> !repository.existsById(suggestion))
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** Coerces arbitrary input into the slug shape so a suggestion is always usable as-is. */
    private static String slugify(String raw) {
        StringBuilder slug = new StringBuilder(raw.length());
        boolean pendingHyphen = false;
        for (int i = 0; i < raw.length(); i++) {
            char current = Character.toLowerCase(raw.charAt(i));
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')) {
                if (pendingHyphen && !slug.isEmpty()) {
                    slug.append('-');
                }
                slug.append(current);
                pendingHyphen = false;
            } else {
                pendingHyphen = true;
            }
        }
        return slug.toString();
    }
}
