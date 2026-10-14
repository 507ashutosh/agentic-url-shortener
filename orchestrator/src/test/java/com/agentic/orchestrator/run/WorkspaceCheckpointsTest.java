package com.agentic.orchestrator.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceCheckpointsTest {

    @TempDir
    Path workspace;

    private WorkspaceCheckpoints checkpoints;

    @BeforeEach
    void setUp() throws IOException {
        write("src/main/App.java", "class App {}");
        write("src/test/AppTest.java", "class AppTest {}");
        checkpoints = WorkspaceCheckpoints.init(workspace);
    }

    private void write(String path, String content) throws IOException {
        Path file = workspace.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(String path) throws IOException {
        return Files.readString(workspace.resolve(path));
    }

    @Test
    void reportsChangedAndNewFilesOnlyInTheGivenFolders() throws IOException {
        write("src/main/App.java", "class App { int x; }");
        write("src/main/New.java", "class New {}");
        write("src/test/AppTest.java", "class AppTest { int y; }");

        List<ChangedFile> changes = checkpoints.uncommittedChanges(List.of("src/main"));

        assertThat(changes).extracting(ChangedFile::path).containsExactlyInAnyOrder(
                "src/main/App.java", "src/main/New.java");
        assertThat(changes).filteredOn(ChangedFile::isNew).extracting(ChangedFile::path)
                .containsExactly("src/main/New.java");
    }

    @Test
    void rollbackRestoresOneFolderAndLeavesTheOthersAlone() throws IOException {
        write("src/main/App.java", "broken");
        write("src/main/New.java", "class New {}");
        write("src/test/AppTest.java", "class AppTest { int y; }");

        String undone = checkpoints.rollback(List.of("src/main"));

        assertThat(undone).isEqualTo("2 files reverted");
        assertThat(read("src/main/App.java")).isEqualTo("class App {}");
        assertThat(workspace.resolve("src/main/New.java")).doesNotExist();
        assertThat(read("src/test/AppTest.java")).isEqualTo("class AppTest { int y; }");   // parallel stage's work kept
    }

    @Test
    void rollbackOnlyUndoesWorkSinceTheLastCommit() throws IOException {
        write("src/main/App.java", "accepted version");
        checkpoints.commit("after implement", List.of("src/main"));
        write("src/main/App.java", "rejected version");

        checkpoints.rollback(List.of("src/main"));

        assertThat(read("src/main/App.java")).isEqualTo("accepted version");
        assertThat(checkpoints.rollback(List.of("src/main"))).isNull();   // nothing left to undo
    }

    @Test
    void rollbackToBaselineUndoesCommittedWorkToo() throws IOException {
        write("src/main/App.java", "accepted version");
        write("docs/feature.md", "# Feature");
        checkpoints.commit("after implement", List.of("src/main"));
        checkpoints.commit("after docs", List.of("docs"));

        checkpoints.rollbackToBaseline(List.of("src/main", "docs"));

        assertThat(read("src/main/App.java")).isEqualTo("class App {}");
        assertThat(workspace.resolve("docs/feature.md")).doesNotExist();
        assertThat(checkpoints.changesSinceBaseline(List.of("src/main", "docs"))).isEmpty();
    }
}
