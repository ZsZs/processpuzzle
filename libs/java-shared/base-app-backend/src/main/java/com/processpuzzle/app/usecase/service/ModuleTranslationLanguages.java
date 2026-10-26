package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.usecase.port.SupportedLanguages;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Which languages a module's translations are prepared in, and which one the authored texts are in.
 *
 * <p>Both questions belong to ports the host may leave unanswered, so each has a fallback: with no
 * {@link SupportedLanguages} the only language prepared is the default one and any well-formed tag is
 * accepted on upload; with no {@link TenantDirectory} locale the default is the first supported language,
 * else {@code en}.
 */
@Component
public class ModuleTranslationLanguages {

    static final String FALLBACK_LOCALE = "en";

    private final ObjectProvider<SupportedLanguages> supportedLanguages;
    private final ObjectProvider<TenantDirectory> tenantDirectory;

    public ModuleTranslationLanguages(ObjectProvider<SupportedLanguages> supportedLanguages,
                                      ObjectProvider<TenantDirectory> tenantDirectory) {
        this.supportedLanguages = supportedLanguages;
        this.tenantDirectory = tenantDirectory;
    }

    /** The language the authored texts are written in. */
    public String defaultLocale(String orgKey) {
        TenantDirectory directory = tenantDirectory.getIfAvailable();
        Optional<String> tenantLocale = directory == null
                ? Optional.empty()
                : directory.find(orgKey).map(TenantDirectory.Tenant::defaultLocale).filter(locale -> !locale.isBlank());
        return tenantLocale.orElseGet(() -> named(orgKey).stream().findFirst().orElse(FALLBACK_LOCALE));
    }

    /** The languages to prepare files for: the supported ones, led by the default one. */
    public List<String> prepared(String orgKey) {
        List<String> named = named(orgKey);
        if (named.isEmpty()) {
            return List.of(defaultLocale(orgKey));
        }
        String defaultLocale = defaultLocale(orgKey);
        return named.stream().anyMatch(defaultLocale::equalsIgnoreCase)
                ? named
                : Stream.concat(Stream.of(defaultLocale), named.stream()).toList();
    }

    /**
     * The spelling the organization uses for {@code locale}, or empty when it is not supported — case
     * differences ({@code pt-br}) are forgiven, and no named list at all means anything well-formed goes.
     */
    public Optional<String> accept(String orgKey, String locale) {
        if (named(orgKey).isEmpty()) {
            return Optional.of(locale);
        }
        return prepared(orgKey).stream().filter(locale::equalsIgnoreCase).findFirst();
    }

    private List<String> named(String orgKey) {
        SupportedLanguages port = supportedLanguages.getIfAvailable();
        return port == null ? List.of() : port.codes(orgKey).stream().filter(code -> !code.isBlank()).toList();
    }
}
