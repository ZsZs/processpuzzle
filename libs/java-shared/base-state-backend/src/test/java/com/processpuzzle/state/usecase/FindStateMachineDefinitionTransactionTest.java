package com.processpuzzle.state.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Over real transactions, because the trap is Spring's: an exception leaving a transactional method marks the
 * caller's transaction rollback-only even when the caller catches it — which made saving an object of a type no
 * state machine governs fail with an {@code UnexpectedRollbackException} (stage, 2026-10-13).
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:state-find;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.state.domain")
@EnableJpaRepositories("com.processpuzzle.state.domain")
@Import(FindStateMachineDefinition.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FindStateMachineDefinitionTransactionTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private FindStateMachineDefinition findStateMachineDefinition;
    @Autowired
    private TransactionTemplate transaction;

    @Test
    void askingForAMachineThereIsNoneOfLeavesTheCallersTransactionCommittable() {
        assertThatCode(() -> transaction.executeWithoutResult(status ->
                assertThat(findStateMachineDefinition.find("my-org", "invoice", Channel.RELEASED)).isEmpty()))
                .doesNotThrowAnyException();
    }

    /** Why a caller to whom "none" is an answer must not catch the exception instead: its commit is refused. */
    @Test
    void catchingTheNotFoundExceptionStillDoomsTheCallersTransaction() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                assertThatThrownBy(() -> findStateMachineDefinition.execute("my-org", "invoice", Channel.RELEASED))
                        .isInstanceOf(StateMachineNotFoundException.class)))
                .isInstanceOf(UnexpectedRollbackException.class);
    }
}
