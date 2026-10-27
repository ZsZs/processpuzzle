package com.processpuzzle.ai.assistant;

import com.processpuzzle.ai.domain.AssistantSession;
import com.processpuzzle.ai.domain.AssistantSessionMessage;
import com.processpuzzle.ai.domain.AssistantSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversations of the design assistant: opened by one designer, read back as the model's recent history, and
 * expired after a period without a message. A session is only ever visible to the designer who opened it, within the
 * organization it was opened in.
 */
public class AssistantSessions {

    static final String USER = "user";
    static final String ASSISTANT = "assistant";

    private final AssistantSessionRepository sessions;
    private final Clock clock;
    private final int historyMessages;
    private final Duration idleTimeout;

    public AssistantSessions(AssistantSessionRepository sessions, Clock clock, int historyMessages, Duration idleTimeout) {
        this.sessions = sessions;
        this.clock = clock;
        this.historyMessages = historyMessages;
        this.idleTimeout = idleTimeout;
    }

    @Transactional
    public UUID open(String organization, String subject) {
        return sessions.save(new AssistantSession(organization, subject, Instant.now(clock))).getId();
    }

    /**
     * The most recent messages of the session, oldest first, for the model to read before the next message.
     *
     * @throws AssistantSessionNotFoundException when the session is not this designer's in this organization
     */
    @Transactional(readOnly = true)
    public List<Message> history(UUID id, String organization, String subject) {
        List<AssistantSessionMessage> messages = find(id, organization, subject).getMessages();
        return messages.subList(Math.max(0, messages.size() - historyMessages), messages.size()).stream()
            .map(AssistantSessions::toSpring)
            .toList();
    }

    /** Records one exchange: the designer's message and the assistant's reply, both at the end of the session. */
    @Transactional
    public void append(UUID id, String organization, String subject, String userText, String replyText) {
        AssistantSession session = find(id, organization, subject);
        Instant now = Instant.now(clock);
        session.append(USER, clip(userText), now);
        session.append(ASSISTANT, clip(replyText), now);
    }

    /** Deletes the sessions idle for longer than the idle timeout, messages first, and reports how many sessions went. */
    @Transactional
    public int purgeIdle() {
        Instant cutoff = Instant.now(clock).minus(idleTimeout);
        sessions.deleteMessagesIdleBefore(cutoff);
        return sessions.deleteIdleBefore(cutoff);
    }

    private AssistantSession find(UUID id, String organization, String subject) {
        return sessions.findByIdAndOrgKeyAndOwnerSubject(id, organization, subject)
            .orElseThrow(() -> new AssistantSessionNotFoundException(id));
    }

    private static Message toSpring(AssistantSessionMessage message) {
        return USER.equals(message.getRole()) ? new UserMessage(message.getText()) : new AssistantMessage(message.getText());
    }

    private static String clip(String text) {
        return text.length() <= AssistantSessionMessage.MAX_TEXT_LENGTH
            ? text
            : text.substring(0, AssistantSessionMessage.MAX_TEXT_LENGTH);
    }
}
