package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.common.ChatMetrics;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.core.definition.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes the conversations nobody has written in for as long as their definition keeps them — storage limitation:
 * personal data is not kept longer than its purpose needs.
 *
 * <p>A conversation is <b>idle</b> when its newest message, or its opening when it has none, is older than the
 * definition's {@code retentionDays}. Idle conversations are deleted whole — messages, read markers and the
 * conversation — whether they are open or closed, because the period is a limit on keeping the data, not a state of
 * the conversation. A conversation that is still being written in is never idle. A definition without
 * {@code retentionDays} keeps its conversations for ever.
 *
 * <p>The released definitions decide. A conversation whose definition is no longer released has no policy to
 * apply and stays until the tenant is erased.
 *
 * <p>Each pass works in batches, one transaction each, so a large backlog is not one long transaction; the next pass
 * takes what is left. {@code base-chat.retention.enabled=false} switches the job off.
 */
@Service
public class ChatRetention {

    private static final Logger LOG = LoggerFactory.getLogger(ChatRetention.class);
    /** How many conversations one transaction deletes. */
    static final int BATCH = 200;
    /** A pass stops after this many batches per definition; the next pass continues. */
    static final int MAX_BATCHES = 50;

    private final ChatDefinitionRepository definitions;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ReadMarkerRepository markers;
    private final TransactionTemplate transaction;
    private final ChatMetrics metrics;
    private final boolean enabled;

    public ChatRetention(ChatDefinitionRepository definitions, ConversationRepository conversations, MessageRepository messages,
                         ReadMarkerRepository markers, TransactionTemplate transaction, ChatMetrics metrics,
                         @Value("${base-chat.retention.enabled:true}") boolean enabled) {
        this.definitions = definitions;
        this.conversations = conversations;
        this.messages = messages;
        this.markers = markers;
        this.transaction = transaction;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${base-chat.retention.interval:PT6H}", initialDelayString = "${base-chat.retention.initial-delay:PT10M}")
    public void scheduledPass() {
        if (enabled) {
            purgeIdle(Instant.now());
        }
    }

    /**
     * @param now the moment the pass is run for; a conversation idle since before {@code now - retentionDays} goes
     * @return how many conversations were deleted
     */
    public int purgeIdle(Instant now) {
        int deleted = 0;
        for (ChatDefinition definition : definitions.findByChannelAndRetentionDaysIsNotNull(Channel.RELEASED)) {
            deleted += purge(definition, now.minus(Duration.ofDays(definition.getRetentionDays())));
        }
        metrics.retentionDeleted(deleted);
        if (deleted > 0) {
            LOG.info("Chat retention deleted {} idle conversation(s).", deleted);
        }
        return deleted;
    }

    private int purge(ChatDefinition definition, Instant cutoff) {
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            Integer removed = transaction.execute(status -> deleteBatch(definition, cutoff));
            if (removed == null || removed == 0) {
                break;
            }
            deleted += removed;
        }
        return deleted;
    }

    private int deleteBatch(ChatDefinition definition, Instant cutoff) {
        String orgKey = definition.getOrgKey();
        List<UUID> ids = conversations.findIdleIds(orgKey, definition.getDefinitionKey(), cutoff, PageRequest.of(0, BATCH));
        if (ids.isEmpty()) {
            return 0;
        }
        markers.deleteByConversationIds(orgKey, ids);
        messages.deleteByConversationIds(orgKey, ids);
        return conversations.deleteByIds(orgKey, ids);
    }
}
