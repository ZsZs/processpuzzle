package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link ModuleAsset}: one asset per organization, module, channel and path. A plain
 * mutable class rather than a record, for the reason given on {@link AppDefinitionKey}.
 */
public class ModuleAssetKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String moduleKey;
    private Channel channel;
    private String path;

    public ModuleAssetKey() {
        // required by JPA
    }

    public ModuleAssetKey(String orgKey, String moduleKey, Channel channel, String path) {
        this.orgKey = orgKey;
        this.moduleKey = moduleKey;
        this.channel = channel;
        this.path = path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModuleAssetKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(moduleKey, that.moduleKey)
                && channel == that.channel && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, moduleKey, channel, path);
    }
}
