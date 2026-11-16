package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.processpuzzle.starter.domain.DataSpaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSpaceDirectoryAdapterTest {

    @Mock
    private DataSpaceRepository dataSpaces;
    @InjectMocks
    private DataSpaceDirectoryAdapter directory;

    @Test
    void member_isInTheCommonOfItsStarter() {
        when(dataSpaces.findCommonKeysOf("club")).thenReturn(List.of("starter-common"));

        assertThat(directory.commonOf("club")).contains("starter-common");
    }

    @Test
    void organizationWithoutAStarterWithACommon_hasNone() {
        when(dataSpaces.findCommonKeysOf("club")).thenReturn(List.of());

        assertThat(directory.commonOf("club")).isEmpty();
    }

    @Test
    void theCommonItself_isNotItsOwnMember() {
        when(dataSpaces.findCommonKeysOf("starter-common")).thenReturn(List.of("starter-common"));

        assertThat(directory.commonOf("starter-common")).isEmpty();
    }

    @Test
    void isCommon_asksTheRegistry() {
        when(dataSpaces.existsById("starter-common")).thenReturn(true);

        assertThat(directory.isCommon("starter-common")).isTrue();
        assertThat(directory.isCommon("club")).isFalse();
    }
}
