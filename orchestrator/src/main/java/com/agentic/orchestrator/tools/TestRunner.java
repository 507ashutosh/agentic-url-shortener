package com.agentic.orchestrator.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.agentic.orchestrator.run.Artifact;
import com.agentic.orchestrator.run.RunContext;

/**
 * The validate stage. Not an AI agent: it compiles the changed shortener in the workspace and
 * runs its full test suite with Maven. Agents propose; this checks.
 */
public final class TestRunner {

    public static final String STAGE_ID = "validate";

    private static final Pattern TOTALS = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)$");
    private static final long TIMEOUT_MINUTES = 10;

    public String run(RunContext context) throws IOException, InterruptedException {
        Path workspace = context.workspace().workspaceDir();
        Path log = context.workspace().artifactsDir().resolve("validate-maven.log");

        ProcessBuilder builder = new ProcessBuilder("./mvnw", "-B", "-f", "shortener/pom.xml", "test")
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        // Build with the same JDK the orchestrator runs on.
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));

        Process process = builder.start();
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Maven did not finish within " + TIMEOUT_MINUTES + " minutes");
        }

        List<String> output = java.nio.file.Files.readAllLines(log);
        String totals = output.stream()
                .map(TOTALS::matcher).filter(Matcher::find)
                .reduce((first, second) -> second)   // the last match is the overall total
                .map(m -> "%s tests run, %s failures, %s errors".formatted(m.group(1), m.group(2), m.group(3)))
                .orElse("no test results found");

        String logPath = context.workspace().runDir().relativize(log).toString();
        if (process.exitValue() != 0) {
            List<String> errors = output.stream().filter(l -> l.startsWith("[ERROR]")).limit(8).toList();
            context.record(new Artifact(STAGE_ID, "FAILED: " + totals,
                    "Build or tests failed. First errors:\n\n```\n" + String.join("\n", errors)
                            + "\n```\n\nFull log: " + logPath, List.of()), List.of());
            throw new IllegalStateException("build or tests failed (" + totals + "), see " + logPath);
        }

        String summary = "build OK, " + totals;
        context.record(new Artifact(STAGE_ID, summary, "Full log: " + logPath, List.of()), List.of());
        return summary;
    }
}
