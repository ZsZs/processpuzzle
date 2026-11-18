package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.SharedTypes;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindEntityDefinitionByCodeUseCase {

    private final EntityDefinitionRepository repository;
    private final ObjectProvider<DataSpaceDirectory> dataSpaces;

    @Transactional(readOnly = true)
    public BaseEntityDefinition findByCode(String orgKey, String code, Channel channel) {
        return repository.findByOrgKeyAndCodeAndChannel(orgKey, code, channel)
            .or(() -> SharedTypes.commonOf(dataSpaces, orgKey)
                .flatMap(common -> repository.findByOrgKeyAndCodeAndChannel(common, code, Channel.RELEASED))
                .filter(BaseEntityDefinition::isShared))
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(code)));
    }
}
