package com.agentic.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelineTest {

    private static final StageAction NOOP = () -> "ok";

    /** requirements → design → (implement, tests, docs) → validate → release */
    private static Pipeline sdlc() {
        return Pipeline.builder("sdlc")
                .stage("requirements", NOOP)
                .stage("design", NOOP, "requirements")
                .stage("implement", NOOP, "design")
                .stage("tests", NOOP, "design")
                .stage("docs", NOOP, "design")
                .stage("validate", NOOP, "implement", "tests", "docs")
                .stage("release", NOOP, "validate")
                .build();
    }

    @Test
    void topologicalOrderPutsEveryStageAfterItsDependencies() {
        Pipeline pipeline = sdlc();
        List<String> order = pipeline.topologicalOrder();

        assertThat(order).hasSize(7);
        for (String id : order) {
            for (String dep : pipeline.stage(id).dependsOn()) {
                assertThat(order.indexOf(dep)).as(dep + " before " + id).isLessThan(order.indexOf(id));
            }
        }
    }

    @Test
    void wavesGroupStagesThatCanRunInParallel() {
        assertThat(sdlc().waves()).containsExactly(
                List.of("requirements"),
                List.of("design"),
                List.of("implement", "tests", "docs"),
                List.of("validate"),
                List.of("release"));
    }

    @Test
    void downstreamOfListsEverythingThatDependsOnAStage() {
        Pipeline pipeline = sdlc();

        assertThat(pipeline.downstreamOf("design"))
                .containsExactly("implement", "tests", "docs", "validate", "release");
        assertThat(pipeline.downstreamOf("docs")).containsExactly("validate", "release");
        assertThat(pipeline.downstreamOf("release")).isEmpty();
    }

    @Test
    void rejectsACycle() {
        Pipeline.Builder builder = Pipeline.builder("cyclic")
                .stage("start", NOOP)
                .stage("a", NOOP, "start", "c")
                .stage("b", NOOP, "a")
                .stage("c", NOOP, "b");

        assertThatThrownBy(builder::build)
                .isInstanceOf(PipelineValidationException.class)
                .hasMessageContaining("Cycle")
                .hasMessageContaining("a").hasMessageContaining("b").hasMessageContaining("c");
    }

    @Test
    void rejectsAStageThatDependsOnItself() {
        Pipeline.Builder builder = Pipeline.builder("self").stage("a", NOOP, "a");

        assertThatThrownBy(builder::build).hasMessageContaining("depends on itself");
    }

    @Test
    void rejectsAnUnknownDependency() {
        Pipeline.Builder builder = Pipeline.builder("typo").stage("design", NOOP, "requirments");

        assertThatThrownBy(builder::build).hasMessageContaining("unknown stage 'requirments'");
    }

    @Test
    void rejectsADuplicateStage() {
        Pipeline.Builder builder = Pipeline.builder("dup").stage("a", NOOP);

        assertThatThrownBy(() -> builder.stage("a", NOOP)).hasMessageContaining("Duplicate stage 'a'");
    }

    @Test
    void rejectsAnEmptyPipeline() {
        assertThatThrownBy(() -> Pipeline.builder("empty").build()).hasMessageContaining("no stages");
    }
}
