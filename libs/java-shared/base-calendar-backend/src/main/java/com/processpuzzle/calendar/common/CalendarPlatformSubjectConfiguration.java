package com.processpuzzle.calendar.common;

import com.processpuzzle.calendar.entry.usecases.inbound.EntryPlatformFacts;
import com.processpuzzle.core.definition.PlatformSubject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares calendar-entry as a subject the platform publishes facts about, so the event catalog can name it and a
 * workflow can start when something is booked: an event definition subject is otherwise required to be an entity type.
 */
@Configuration(proxyBeanMethods = false)
public class CalendarPlatformSubjectConfiguration {

    @Bean
    public PlatformSubject calendarEntryPlatformSubject() {
        return new PlatformSubject(EntryPlatformFacts.SUBJECT_TYPE, EntryPlatformFacts.FACT_ATTRIBUTES);
    }
}
