package com.agentic.orchestrator.scenario;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads scenario files from the repository's scenarios/ folder. */
public final class ScenarioLoader {

    private final Path scenariosDir;

    public ScenarioLoader(Path scenariosDir) {
        this.scenariosDir = scenariosDir;
    }

    public Scenario load(String name) {
        Path file = scenariosDir.resolve(name + ".yaml");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Scenario file not found: " + file);
        }
        // SafeConstructor: plain maps and strings only, never arbitrary Java objects.
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            return new Scenario(
                    required(data, "name", file),
                    required(data, "type", file),
                    required(data, "title", file),
                    required(data, "requirement", file).strip(),
                    String.valueOf(data.getOrDefault("baseline", "repo")));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    private static String required(Map<String, Object> data, String key, Path file) {
        Object value = data == null ? null : data.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(file + " is missing '" + key + "'");
        }
        return value.toString();
    }
}
