package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The translations of one module into one language, as the user uploaded them: a flat map from a key
 * relative to the module's transloco scope to the translated text.
 *
 * <p>Keyed by the <em>module</em> and not by its scope, so renaming the scope leaves the translations
 * attached. Stored per {@link Channel} like the module itself: an upload writes the {@code DRAFT}, and
 * only a release of the organization copies it onto the {@code RELEASED} row the running application
 * loads. Separate from {@code AppTranslationBundle}, which holds the framework's own seeded bundles
 * rather than tenant content.
 */
@Getter
@Entity
@Table(name = "app_module_translations")
@IdClass(ModuleTranslationKey.class)
public class ModuleTranslation {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "module_key", length = 100)
    private String moduleKey;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel;

    @Id
    @Column(name = "locale", length = 16)
    private String locale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> messages = new LinkedHashMap<>();

    protected ModuleTranslation() {
        // required by JPA
    }

    public ModuleTranslation(String orgKey, String moduleKey, Channel channel, String locale,
                             Map<String, String> messages) {
        this.orgKey = orgKey;
        this.moduleKey = moduleKey;
        this.channel = channel;
        this.locale = locale;
        setMessages(messages);
    }

    public void setMessages(Map<String, String> messages) {
        this.messages = messages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(messages);
    }
}
