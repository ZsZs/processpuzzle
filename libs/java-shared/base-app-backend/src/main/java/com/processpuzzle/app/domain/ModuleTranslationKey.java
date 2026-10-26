package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link ModuleTranslation}: one bundle per organization, module, channel and
 * language. A plain mutable class rather than a record, for the reason given on {@link AppDefinitionKey}.
 */
public class ModuleTranslationKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String moduleKey;
    private Channel channel;
    private String locale;

    public ModuleTranslationKey() {
        // required by JPA
    }

    public ModuleTranslationKey(String orgKey, String moduleKey, Channel channel, String locale) {
        this.orgKey = orgKey;
        this.moduleKey = moduleKey;
        this.channel = channel;
        this.locale = locale;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModuleTranslationKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(moduleKey, that.moduleKey)
                && channel == that.channel && Objects.equals(locale, that.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, moduleKey, channel, locale);
    }
}
