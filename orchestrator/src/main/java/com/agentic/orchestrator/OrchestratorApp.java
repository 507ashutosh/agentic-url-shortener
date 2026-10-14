package com.agentic.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentic.orchestrator.console.ConsoleRunListener;
import com.agentic.orchestrator.engine.DagExecutor;
import com.agentic.orchestrator.engine.Pipeline;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.llm.ClaudeLlmClient;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.ScriptedLlmClient;
import com.agentic.orchestrator.pipeline.SdlcPipelineFactory;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.RunWorkspace;
import com.agentic.orchestrator.scenario.Scenario;
import com.agentic.orchestrator.scenario.ScenarioLoader;

/**
 * Command-line entry point. Run from the repository root.
 *
 * <pre>
 * java -jar orchestrator/target/orchestrator.jar --scenario brownfield          mock agents (no API key)
 * java -jar orchestrator/target/orchestrator.jar --scenario brownfield --live   real Claude agents
 * </pre>
 */
public final class OrchestratorApp {

    private OrchestratorApp() {
    }

    public static void main(String[] args) {
        String scenarioName = "brownfield";
        boolean live = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--scenario" -> scenarioName = valueAfter(args, i++);
                case "--live" -> live = true;
                default -> usage("Unknown argument: " + args[i]);
            }
        }

        Path repo = Path.of("").toAbsolutePath();
        if (!Files.isRegularFile(repo.resolve("shortener/pom.xml"))) {
            usage("Run this from the repository root (the folder containing shortener/ and scenarios/)");
        }

        Scenario scenario = new ScenarioLoader(repo.resolve("scenarios")).load(scenarioName);
        LlmClient llm = live ? ClaudeLlmClient.fromEnv() : new ScriptedLlmClient(scenario.name());
        RunWorkspace workspace = RunWorkspace.create(repo, scenario.name());
        RunContext context = new RunContext(scenario, workspace);

        System.out.printf("Scenario: %s (%s) · agents: %s%n", scenario.title(), scenario.type(), llm.describe());
        System.out.printf("Requirement: \"%s\"%n", scenario.requirement());
        System.out.printf("Run folder: %s%n%n", repo.relativize(workspace.runDir()));

        Pipeline pipeline = SdlcPipelineFactory.create(llm, context);
        RunReport report = new DagExecutor(new ConsoleRunListener(System.out)).run(pipeline);

        printLineage(context.decisions());
        System.out.printf("%nStage reports: %s%n", repo.relativize(workspace.artifactsDir()));
        System.exit(report.succeeded() ? 0 : 1);
    }

    /** Shows what each stage decided or assumed and which earlier stages it relied on. */
    private static void printLineage(List<Decision> decisions) {
        if (decisions.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("Decision lineage:");
        for (Decision d : decisions) {
            String basedOn = d.basedOn().isEmpty() ? "" : "  ← " + String.join(", ", d.basedOn());
            System.out.printf("  [%s] %-10s %s%s%n", d.stageId(), d.kind(), d.text(), basedOn);
        }
    }

    private static String valueAfter(String[] args, int i) {
        if (i + 1 >= args.length) {
            usage(args[i] + " needs a value");
        }
        return args[i + 1];
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("Usage: java -jar orchestrator/target/orchestrator.jar [--scenario <name>] [--live]");
        System.exit(2);
    }
}
