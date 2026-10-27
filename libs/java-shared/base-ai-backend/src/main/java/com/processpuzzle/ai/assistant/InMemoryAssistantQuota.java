package com.processpuzzle.ai.assistant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link AssistantQuota}: per organization and UTC day, the turns and tokens used so far. Counts reset when
 * the day changes; a day's counts are dropped on the first use after it ends.
 */
public class InMemoryAssistantQuota implements AssistantQuota {

    private final Clock clock;
    private final int maxTurnsPerDay;
    private final long maxTokensPerDay;
    private final Map<String, Usage> usage = new ConcurrentHashMap<>();

    public InMemoryAssistantQuota(Clock clock, int maxTurnsPerDay, long maxTokensPerDay) {
        this.clock = clock;
        this.maxTurnsPerDay = maxTurnsPerDay;
        this.maxTokensPerDay = maxTokensPerDay;
    }

    @Override
    public void checkTurnAllowed(String organization) {
        Usage today = today(organization);
        if (today.turns() >= maxTurnsPerDay || today.tokens() >= maxTokensPerDay) {
            throw new AssistantQuotaExceededException(organization);
        }
    }

    @Override
    public void recordTurn(String organization, long tokens) {
        usage.compute(organization, (key, current) -> {
            Usage base = current == null || !current.day().equals(today()) ? Usage.empty(today()) : current;
            return base.plus(tokens);
        });
    }

    private Usage today(String organization) {
        Usage current = usage.get(organization);
        return current == null || !current.day().equals(today()) ? Usage.empty(today()) : current;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private record Usage(LocalDate day, int turns, long tokens) {

        static Usage empty(LocalDate day) {
            return new Usage(day, 0, 0);
        }

        Usage plus(long moreTokens) {
            return new Usage(day, turns + 1, tokens + moreTokens);
        }
    }
}
