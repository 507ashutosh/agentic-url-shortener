package com.agentic.orchestrator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.agentic.orchestrator.console.ConsoleRunListener;
import com.agentic.orchestrator.engine.DagExecutor;
import com.agentic.orchestrator.engine.RunReport;
import com.agentic.orchestrator.governance.approval.ApprovalGateway;
import com.agentic.orchestrator.governance.approval.AutoApprovalGateway;
import com.agentic.orchestrator.governance.approval.ConsoleApprovalGateway;
import com.agentic.orchestrator.llm.ClaudeLlmClient;
import com.agentic.orchestrator.llm.FallbackLlmClient;
import com.agentic.orchestrator.llm.FaultInjectingLlmClient;
import com.agentic.orchestrator.llm.FaultInjectingLlmClient.Fault;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.ScriptedLlmClient;
import com.agentic.orchestrator.pipeline.SdlcPipelineFactory;
import com.agentic.orchestrator.pipeline.SdlcPipelineFactory.SdlcPipeline;
import com.agentic.orchestrator.run.Decision;
import com.agentic.orchestrator.run.RunContext;
import com.agentic.orchestrator.run.RunContext.Revision;
import com.agentic.orchestrator.run.RunWorkspace;
import com.agentic.orchestrator.run.WorkspaceCheckpoints;
import com.agentic.orchestrator.scenario.Scenario;
import com.agentic.orchestrator.scenario.ScenarioLoader;

/**
 * Command-line entry point. Run from the repository root.
 *
 * <pre>
 * java -jar orchestrator/target/orchestrator.jar --scenario brownfield
 *     --live                      real Claude agents instead of recorded responses
 *     --auto-approve              approve every checkpoint automatically (unattended runs)
 *     --approver &lt;name&gt;           name recorded on approvals (default: your OS user)
 *     --inject-fault &lt;stage:kind&gt;  error | outage | secret | broken-code (repeatable)
 * </pre>
 */
public final class OrchestratorApp {

    /** After this many failed calls for one agent, the live model is bypassed for the recorded response. */
    private static final int FAILURES_BEFORE_FALLBACK = 2;

    private OrchestratorApp() {
    }

    public static void main(String[] args) {
        String scenarioName = "brownfield";
        boolean live = false;
        boolean autoApprove = false;
        String approver = System.getProperty("user.name");
        List<Fault> faults = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--scenario" -> scenarioName = valueAfter(args, i++);
                case "--live" -> live = true;
                case "--auto-approve" -> autoApprove = true;
                case "--approver" -> approver = valueAfter(args, i++);
                case "--inject-fault" -> faults.add(Fault.parse(valueAfter(args, i++)));
                default -> usage("Unknown argument: " + args[i]);
            }
        }

        Path repo = Path.of("").toAbsolutePath();
        if (!Files.isRegularFile(repo.resolve("shortener/pom.xml"))) {
            usage("Run this from the repository root (the folder containing shortener/ and scenarios/)");
        }

        PrintStream out = System.out;
        ConsoleRunListener console = new ConsoleRunListener(out);
        Scenario scenario = new ScenarioLoader(repo.resolve("scenarios")).load(scenarioName);
        RunWorkspace workspace = RunWorkspace.create(repo, scenario.name());
        WorkspaceCheckpoints checkpoints = WorkspaceCheckpoints.init(workspace.workspaceDir());
        RunContext context = new RunContext(scenario, workspace);

        LlmClient model = live ? ClaudeLlmClient.fromEnv() : new ScriptedLlmClient(scenario.name());
        if (!faults.isEmpty()) {
            model = new FaultInjectingLlmClient(model, faults);
        }
        LlmClient llm = new FallbackLlmClient(model, new ScriptedLlmClient(scenario.name()),
                FAILURES_BEFORE_FALLBACK, (agent, reason) -> console.note(agent, "fallback: " + reason));
        ApprovalGateway approvals = autoApprove
                ? new AutoApprovalGateway()
                : new ConsoleApprovalGateway(out, new BufferedReader(new InputStreamReader(System.in)), approver);

        out.printf("Scenario: %s (%s) · agents: %s%n", scenario.title(), scenario.type(), model.describe());
        out.printf("Requirement: \"%s\"%n", scenario.requirement());
        out.printf("Approvals: %s · run folder: %s%n%n", autoApprove ? "automatic" : "by " + approver,
                repo.relativize(workspace.runDir()));

        SdlcPipeline sdlc = SdlcPipelineFactory.create(llm, context, checkpoints, approvals);
        DagExecutor executor = new DagExecutor(console);
        RunReport report = executor.run(sdlc.pipeline());

        // A reviewer sent a stage back: reset what it and its downstream stages wrote, then re-run only those.
        for (Optional<Revision> revision = context.takeRevision(); revision.isPresent();
             revision = context.takeRevision()) {
            String stage = revision.get().stageId();
            List<String> affected = new ArrayList<>(List.of(stage));
            affected.addAll(sdlc.pipeline().downstreamOf(stage));
            affected.forEach(id -> checkpoints.rollbackToBaseline(sdlc.foldersOf(id)));
            if (!live) {
                out.println("  (mock mode: recorded responses cannot react to feedback; live agents would)");
            }
            report = executor.rerun(sdlc.pipeline(), report, stage);
        }

        printGovernance(out, context);
        out.printf("%nStage reports: %s%n", repo.relativize(workspace.artifactsDir()));
        System.exit(report.succeeded() ? 0 : 1);
    }

    /** Decision lineage, approvals and policy findings: what was decided, by whom, and why. */
    private static void printGovernance(PrintStream out, RunContext context) {
        List<Decision> decisions = context.decisions();
        if (!decisions.isEmpty()) {
            out.println();
            out.println("Decision lineage:");
            for (Decision d : decisions) {
                String basedOn = d.basedOn().isEmpty() ? "" : "  ← " + String.join(", ", d.basedOn());
                out.printf("  [%s] %-10s %s%s%n", d.stageId(), d.kind(), d.text(), basedOn);
            }
        }
        if (!context.approvals().isEmpty()) {
            out.println();
            out.println("Approvals:");
            context.approvals().forEach(a -> out.printf("  [%s] %-28s %s by %s%s%n", a.request().stageId(),
                    a.request().policyId(), a.decision().outcome(), a.decision().approver(),
                    a.decision().feedback() == null ? "" : " · \"" + a.decision().feedback() + "\""));
        }
        if (!context.policyFindings().isEmpty()) {
            out.println();
            out.println("Policy findings:");
            context.policyFindings().forEach(f -> out.printf("  [%s] %-28s %s · %s%n", f.stageId(),
                    f.finding().policyId(), f.finding().severity(), f.finding().reason()));
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
        System.err.println("Usage: java -jar orchestrator/target/orchestrator.jar [--scenario <name>] [--live]"
                + " [--auto-approve] [--approver <name>] [--inject-fault <stage>:<error|outage|secret|broken-code>]");
        System.exit(2);
    }
}
