package com.agentic.orchestrator.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An explicit dependency graph of stages (a DAG: directed, no cycles).
 * Validated when built, so an invalid pipeline can never start running.
 */
public final class Pipeline {

    private final String name;
    private final Map<String, Stage> stages;
    private final List<String> topologicalOrder;

    private Pipeline(String name, Map<String, Stage> stages) {
        this.name = name;
        this.stages = Collections.unmodifiableMap(stages);
        this.topologicalOrder = List.copyOf(sortTopologically(stages));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public Stage stage(String id) {
        Stage stage = stages.get(id);
        if (stage == null) {
            throw new IllegalArgumentException("No stage '" + id + "' in pipeline '" + name + "'");
        }
        return stage;
    }

    public int size() {
        return stages.size();
    }

    /** Stage ids ordered so that every stage comes after everything it depends on. */
    public List<String> topologicalOrder() {
        return topologicalOrder;
    }

    /**
     * Groups stages into waves: wave 1 has no dependencies, and each later wave depends only
     * on earlier ones. Stages in the same wave can run in parallel.
     */
    public List<List<String>> waves() {
        Map<String, Integer> waveOf = new HashMap<>();
        List<List<String>> waves = new ArrayList<>();
        for (String id : topologicalOrder) {
            int wave = stage(id).dependsOn().stream().mapToInt(dep -> waveOf.get(dep) + 1).max().orElse(0);
            waveOf.put(id, wave);
            if (wave == waves.size()) {
                waves.add(new ArrayList<>());
            }
            waves.get(wave).add(id);
        }
        return waves.stream().map(List::copyOf).toList();
    }

    /** Every stage that depends on the given one, directly or indirectly, in dependency order. */
    public List<String> downstreamOf(String id) {
        stage(id);
        Set<String> affected = new LinkedHashSet<>();
        affected.add(id);
        List<String> downstream = new ArrayList<>();
        for (String candidate : topologicalOrder) {
            if (stage(candidate).dependsOn().stream().anyMatch(affected::contains)) {
                affected.add(candidate);
                downstream.add(candidate);
            }
        }
        return downstream;
    }

    /**
     * Kahn's algorithm: repeatedly take a stage whose dependencies are all placed.
     * If stages remain that can never be placed, they form a cycle.
     */
    private static List<String> sortTopologically(Map<String, Stage> stages) {
        Map<String, Integer> unmetDependencies = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (Stage stage : stages.values()) {
            unmetDependencies.put(stage.id(), stage.dependsOn().size());
            for (String dep : stage.dependsOn()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(stage.id());
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        unmetDependencies.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (unmetDependencies.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() < stages.size()) {
            List<String> stuck = stages.keySet().stream().filter(id -> !order.contains(id)).toList();
            throw new PipelineValidationException("Cycle detected among stages " + stuck);
        }
        return order;
    }

    public static final class Builder {

        private final String name;
        private final Map<String, Stage> stages = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder stage(String id, StageAction action, String... dependsOn) {
            if (stages.containsKey(id)) {
                throw new PipelineValidationException("Duplicate stage '" + id + "'");
            }
            stages.put(id, new Stage(id, Arrays.asList(dependsOn), action));
            return this;
        }

        public Pipeline build() {
            if (stages.isEmpty()) {
                throw new PipelineValidationException("Pipeline '" + name + "' has no stages");
            }
            for (Stage stage : stages.values()) {
                for (String dep : stage.dependsOn()) {
                    if (dep.equals(stage.id())) {
                        throw new PipelineValidationException("Stage '" + stage.id() + "' depends on itself");
                    }
                    if (!stages.containsKey(dep)) {
                        throw new PipelineValidationException(
                                "Stage '" + stage.id() + "' depends on unknown stage '" + dep + "'");
                    }
                }
            }
            return new Pipeline(name, new LinkedHashMap<>(stages));
        }
    }
}
