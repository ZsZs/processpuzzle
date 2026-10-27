package com.processpuzzle.ai.assistant;

/**
 * How much assistant work an organization may do in a day. A turn is checked before the model is called, and its
 * tokens are recorded once it has answered; a turn over the limit never reaches the model.
 *
 * <p>The default keeps the counts in memory, so it limits one instance only. An application running several instances
 * declares a shared {@code AssistantQuota} bean.
 */
public interface AssistantQuota {

    /**
     * @throws AssistantQuotaExceededException when the organization has used up its turns or its tokens for the day
     */
    void checkTurnAllowed(String organization);

    /** Records a finished turn and the tokens it used. */
    void recordTurn(String organization, long tokens);
}
