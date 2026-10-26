package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key of {@link ModuleAsset}: one asset per organization, module, channel and path. A plain
 * mutable class rather than a record, for the reason given on {@link AppDefinitionKey}.
 */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ModuleAssetKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String moduleKey;
    private Channel channel;
    private String path;
}
