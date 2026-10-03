package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.MediaStoreUnavailableException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class MediaStoresTest {

    @Test
    void aUniqueStoreIsUsedWithoutWrappingIt() {
        MediaStore store = mock(MediaStore.class);
        var beans = new StaticListableBeanFactory(Map.of("store", store));

        assertThat(new MediaStores(beans.getBeanProvider(MediaStore.class)).get()).isSameAs(store);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void missingOrAmbiguousStoresRefuseEveryOperation(int count) {
        var beans = new StaticListableBeanFactory();
        for (int i = 0; i < count; i++) {
            beans.addBean("store" + i, mock(MediaStore.class));
        }
        MediaStore store = new MediaStores(beans.getBeanProvider(MediaStore.class)).get();
        Duration expiry = Duration.ofMinutes(5);

        assertThatThrownBy(() -> store.uploadUrl("photo", "image/jpeg", expiry))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
        assertThatThrownBy(() -> store.readUrl("photo", expiry))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
        assertThatThrownBy(() -> store.internalReadUrl("photo", expiry))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
        assertThatThrownBy(() -> store.exists("photo"))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
        assertThatThrownBy(() -> store.put("photo", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
        assertThatThrownBy(() -> store.delete("photo"))
                .isInstanceOf(MediaStoreUnavailableException.class).hasMessageContaining("No media store is configured");
    }
}
