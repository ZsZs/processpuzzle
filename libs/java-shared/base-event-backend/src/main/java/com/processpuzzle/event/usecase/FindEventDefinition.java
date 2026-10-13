package com.processpuzzle.event.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.shared.event.CatalogEventKind;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindEventDefinition {

    private final EventDefinitionRepository repository;

    public FindEventDefinition(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    /** A designer's question, about the draft or the released row. */
    public EventDefinition find(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new EventDefinitionNotFoundException(orgKey, id));
    }

    /**
     * The released definition, or empty when the organization released none of that id: what the running application
     * matches a thrown event against.
     */
    public Optional<EventDefinition> released(String orgKey, String id) {
        return id == null ? Optional.empty() : repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.RELEASED);
    }

    /**
     * The kind of the draft of that id. Asked by base-workflow while a designer saves a workflow, so about the drafts
     * the workflow is designed against.
     */
    public Optional<CatalogEventKind> kindOf(String orgKey, String id) {
        return id == null ? Optional.empty() : repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)
                .map(definition -> CatalogEventKind.valueOf(definition.getKind().name()));
    }

    /** Whether a draft of that id exists; see {@link #kindOf}. */
    public boolean exists(String orgKey, String id) {
        return id != null && repository.existsByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT);
    }

    public List<EventDefinition> findAll(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, channel);
    }
}
