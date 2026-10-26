package com.processpuzzle.app.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModuleAssetPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {"welcome.md", "docs/img/flow.png", "a-b_c.d/e", ".hidden", "v1.2/notes.txt"})
    void acceptsRelativePathsOfPlainSegments(String path) {
        assertThat(ModuleAssetPaths.require(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/abs.md", "docs/", "a//b", "../up.md", "a/../b", "a/./b", ".", "..", "a b.md", "ü.md", "a\\b", "a?b", "a#b", "a%20b"})
    void rejectsAnythingThatIsNotOne(String path) {
        assertThatThrownBy(() -> ModuleAssetPaths.require(path)).isInstanceOf(ModuleAssetRejectedException.class);
    }

    @Test
    void rejectsAMissingOrBlankOrOverlongPath() {
        String tooLong = "a".repeat(ModuleAssetPaths.MAX_LENGTH + 1);
        String atLimit = "a".repeat(ModuleAssetPaths.MAX_LENGTH);

        assertThatThrownBy(() -> ModuleAssetPaths.require(null)).isInstanceOf(ModuleAssetRejectedException.class);
        assertThatThrownBy(() -> ModuleAssetPaths.require(" ")).isInstanceOf(ModuleAssetRejectedException.class);
        assertThatThrownBy(() -> ModuleAssetPaths.require(tooLong)).isInstanceOf(ModuleAssetRejectedException.class);
        assertThat(ModuleAssetPaths.require(atLimit)).hasSize(ModuleAssetPaths.MAX_LENGTH);
    }

    @Test
    void anUploadWithoutAPathTakesItsFileNameWithoutAnyDirectory() {
        assertThat(ModuleAssetPaths.fileNameOf("C:\\Users\\me\\welcome.md")).isEqualTo("welcome.md");
        assertThat(ModuleAssetPaths.fileNameOf("some/dir/pic.png")).isEqualTo("pic.png");
        assertThat(ModuleAssetPaths.fileNameOf("plain.md")).isEqualTo("plain.md");
        assertThat(ModuleAssetPaths.fileNameOf(null)).isNull();
    }
}
