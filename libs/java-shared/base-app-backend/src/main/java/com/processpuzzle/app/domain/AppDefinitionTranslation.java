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
 * The texts of an application's own pages in one language, as the user uploaded them: a flat map from a key to the
 * translated text — {@link ModuleTranslation} one level up. A module's keys are relative to its transloco scope and
 * derived from its routes; an app's are whatever its widgets name under their {@code translocoPrefix}, so nothing
 * here knows which keys exist.
 *
 * <p>Stored per {@link Channel} like the app's assets: an upload writes the {@code DRAFT}, and publishing the app
 * copies it onto the {@code RELEASED} row the running application loads. Separate from {@code AppTranslationBundle},
 * which holds the framework's own seeded bundles rather than tenant content.
 */
@Getter
@Entity
@Table(name = "app_definition_translations")
@IdClass(AppDefinitionTranslationKey.class)
public class AppDefinitionTranslation {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "app_id", length = 100)
    private String appId;

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

    protected AppDefinitionTranslation() {
        // required by JPA
    }

    public AppDefinitionTranslation(String orgKey, String appId, Channel channel, String locale,
                                    Map<String, String> messages) {
        this.orgKey = orgKey;
        this.appId = appId;
        this.channel = channel;
        this.locale = locale;
        setMessages(messages);
    }

    public void setMessages(Map<String, String> messages) {
        this.messages = messages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(messages);
    }
}
