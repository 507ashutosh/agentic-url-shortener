package com.agentic.orchestrator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.agentic.orchestrator.console.ConsoleRunListener;
import com.agentic.orchestrator.engine.CompositeRunListener;
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
import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.observability.AuditSink;
import com.agentic.orchestrator.observability.MetricsReport;
import com.agentic.orchestrator.observability.RunMetrics;
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
 *
 * java -jar orchestrator/target/orchestrator.jar --metrics          reliability metrics across all runs
 * java -jar orchestrator/target/orchestrator.jar --verify-audit &lt;audit.jsonl&gt;   check the hash chain
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
                case "--metrics" -> {
                    MetricsReport.printAndSave(Path.of("runs").toAbsolutePath(), System.out);
                    return;
                }
                case "--verify-audit" -> {
                    verifyAudit(Path.of(valueAfter(args, i)));
                    return;
                }
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
        RunWorkspace workspace = RunWorkspace.create(repo, scenario.name(), scenario.startsFromScaffold());
        WorkspaceCheckpoints checkpoints = WorkspaceCheckpoints.init(workspace.workspaceDir());
        AuditLog audit = new AuditLog(workspace.runDir().resolve("audit.jsonl"), workspace.runId());
        RunContext context = new RunContext(scenario, workspace, audit);

        LlmClient model = live ? ClaudeLlmClient.fromEnv() : new ScriptedLlmClient(scenario.name());
        if (!faults.isEmpty()) {
            model = new FaultInjectingLlmClient(model, faults);
        }
        LlmClient llm = new FallbackLlmClient(model, new ScriptedLlmClient(scenario.name()),
                FAILURES_BEFORE_FALLBACK, (agent, reason) -> {
                    console.note(agent, "fallback: " + reason);
                    audit.record("llm.fallback", agent, AuditSink.data("reason", reason));
                });
        ApprovalGateway approvals = autoApprove
                ? new AutoApprovalGateway()
                : new ConsoleApprovalGateway(out, new BufferedReader(new InputStreamReader(System.in)), approver);

        out.printf("Scenario: %s (%s) · agents: %s%n", scenario.title(), scenario.type(), model.describe());
        out.printf("Requirement: \"%s\"%n", scenario.requirement());
        out.printf("Approvals: %s · run folder: %s%n%n", autoApprove ? "automatic" : "by " + approver,
                repo.relativize(workspace.runDir()));

        SdlcPipeline sdlc = SdlcPipelineFactory.create(llm, context, checkpoints, approvals);
        audit.record("run.configured", null, AuditSink.data("scenario", scenario.name(),
                "requirement", scenario.requirement(), "agents", model.describe(),
                "approvals", autoApprove ? "automatic" : "interactive", "approver", autoApprove ? null : approver,
                "faults", faults.stream().map(Fault::toString).toList()));
        DagExecutor executor = new DagExecutor(new CompositeRunListener(console, audit));
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

        RunMetrics metrics = audit.complete(report, scenario.name(), model.describe());
        MetricsReport.save(metrics, workspace.runDir());

        printGovernance(out, context);
        out.printf(Locale.ROOT, "%nTime: %.1fs total · %.1fs agents and tools · %.1fs waiting for approvals%n",
                metrics.wallClockMs() / 1000.0, metrics.agentTimeMs() / 1000.0, metrics.humanWaitMs() / 1000.0);
        out.printf("Stage reports: %s%n", repo.relativize(workspace.artifactsDir()));
        out.printf("Audit log:     %s (%s)%n", repo.relativize(audit.file()),
                AuditLog.verify(audit.file()) == -1 ? "hash chain verified" : "HASH CHAIN BROKEN");
        out.printf("Run summary:   %s · all runs: --metrics%n",
                repo.relativize(workspace.runDir().resolve(MetricsReport.SUMMARY_FILE)));
        System.exit(report.succeeded() ? 0 : 1);
    }

    private static void verifyAudit(Path file) {
        long broken = AuditLog.verify(file);
        if (broken == -1) {
            System.out.println("OK: hash chain intact, no event was changed or removed · " + file);
            return;
        }
        System.out.println("TAMPERED: hash chain breaks at event " + broken + " · " + file);
        System.exit(1);
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
            context.approvals().forEach(a -> out.printf(Locale.ROOT, "  [%s] %-28s %s by %s after %.1fs%s%n",
                    a.request().stageId(), a.request().policyId(), a.decision().outcome(), a.decision().approver(),
                    a.waited().toMillis() / 1000.0,
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
        System.err.println("       java -jar orchestrator/target/orchestrator.jar --metrics");
        System.err.println("       java -jar orchestrator/target/orchestrator.jar --verify-audit <audit.jsonl>");
        System.exit(2);
    }
}
