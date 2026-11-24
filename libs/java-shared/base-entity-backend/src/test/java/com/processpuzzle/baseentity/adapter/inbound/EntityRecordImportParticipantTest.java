package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.RecordPlanner.RecordInput;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase.Action;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase.Item;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase.Report;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityRecordImportParticipantTest {

    private static final String ORG = "acme";

    @Mock
    private LoadRecordsUseCase loader;

    @Test
    void loads_the_entities_of_the_file_and_reports_the_records_created() {
        when(loader.load(eq(ORG), anyList(), eq(false))).thenReturn(new Report(false, List.of(new Item(0, "boat-class", Action.CREATED, "id-1", ""))));
        EntityRecordImportParticipant participant = new EntityRecordImportParticipant(loader);

        ParticipantResult result = participant.load(ORG, "records/seed.yaml", yaml("""
                entities:
                  - entityDefinitionCode: boat-class
                    payload: {name: "470", yardstick: 103}
                """));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem("boat-class id-1", ImportedItem.Action.CREATE));
        ArgumentCaptor<List<RecordInput>> records = ArgumentCaptor.forClass(List.class);
        verify(loader).load(eq(ORG), records.capture(), eq(false));
        assertThat(records.getValue()).containsExactly(new RecordInput("boat-class", Map.of("name", "470", "yardstick", 103)));
    }

    @Test
    void a_refused_record_refuses_the_file_with_its_position_and_reason() {
        when(loader.load(eq(ORG), anyList(), eq(false))).thenReturn(new Report(false, List.of(
                new Item(0, "boat-class", Action.CREATED, "id-1", ""),
                new Item(1, "boat-class", Action.REJECTED, null, "a value is required"))));
        EntityRecordImportParticipant participant = new EntityRecordImportParticipant(loader);

        ParticipantResult result = participant.load(ORG, "records/seed.yaml", yaml("""
                entities:
                  - {entityDefinitionCode: boat-class, payload: {name: "470"}}
                  - {entityDefinitionCode: boat-class, payload: {name: "49er"}}
                """));

        assertThat(result.errors()).containsExactly("Record 2 ('boat-class') is refused: a value is required.");
    }

    @Test
    void a_file_that_is_not_a_list_of_entities_is_refused_before_anything_is_loaded() {
        EntityRecordImportParticipant participant = new EntityRecordImportParticipant(loader);

        ParticipantResult result = participant.load(ORG, "records/seed.yaml", yaml("entities: [unclosed"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors().get(0)).startsWith("The file is not a list of entity records");
        verifyNoInteractions(loader);
    }

    private static byte[] yaml(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
