package com.agentic.orchestrator.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chaos testing: deliberately breaks chosen agents' model calls, so the safety mechanisms
 * can be demonstrated and tested on demand. Enabled with --inject-fault &lt;stage&gt;:&lt;kind&gt;.
 */
public final class FaultInjectingLlmClient implements LlmClient {

    public enum Kind {
        /** The call fails once (a transient error); a retry succeeds. */
        ERROR(1),
        /** Every call fails (the model is down); the circuit breaker switches to the fallback. */
        OUTAGE(Integer.MAX_VALUE),
        /** The first response contains a hard-coded credential; the secret scan must catch it. */
        SECRET(1),
        /** Every response adds code that does not compile; validation must catch it. */
        BROKEN_CODE(Integer.MAX_VALUE);

        private final int times;

        Kind(int times) {
            this.times = times;
        }
    }

    public record Fault(String agentId, Kind kind) {

        /** Parses "tests:error", "design:outage", "docs:secret", "implement:broken-code". */
        public static Fault parse(String spec) {
            String[] parts = spec.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected <stage>:<kind>, got '" + spec + "'");
            }
            return new Fault(parts[0], Kind.valueOf(parts[1].toUpperCase().replace('-', '_')));
        }

        @Override
        public String toString() {
            return agentId + ":" + kind.name().toLowerCase().replace('_', '-');
        }
    }

    private static final String FAKE_SECRET = "api_key = \"AKIAFAKEFAULTINJECT1\"";
    private static final String BROKEN_JAVA = "this line was injected and does not compile;";

    private final LlmClient delegate;
    private final Map<String, Fault> faults = new HashMap<>();
    private final Map<String, AtomicInteger> injected = new HashMap<>();

    public FaultInjectingLlmClient(LlmClient delegate, List<Fault> faults) {
        this.delegate = delegate;
        faults.forEach(f -> {
            this.faults.put(f.agentId(), f);
            this.injected.put(f.agentId(), new AtomicInteger());
        });
    }

    @Override
    public String complete(LlmRequest request) {
        Fault fault = faults.get(request.agentId());
        if (fault == null || injected.get(request.agentId()).getAndIncrement() >= fault.kind().times) {
            return delegate.complete(request);
        }
        return switch (fault.kind()) {
            case ERROR -> throw new LlmException("injected transient error (" + request.agentId() + ")");
            case OUTAGE -> throw new LlmException("injected outage: model unavailable");
            case SECRET -> insertIntoFirstFile(delegate.complete(request), FAKE_SECRET, "");
            case BROKEN_CODE -> insertIntoFirstFile(delegate.complete(request), BROKEN_JAVA, ".java");
        };
    }

    // Adds a line at the end of the first FILE block whose path ends with the suffix.
    private static String insertIntoFirstFile(String response, String line, String suffix) {
        String[] lines = response.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inTarget = false;
        boolean done = false;
        for (String l : lines) {
            if (!done && l.startsWith("<<<FILE ") && l.strip().endsWith(suffix)) {
                inTarget = true;
            } else if (inTarget && l.strip().equals(">>>")) {
                out.append(line).append("\n");
                inTarget = false;
                done = true;
            }
            out.append(l).append("\n");
        }
        return out.substring(0, out.length() - 1);
    }

    @Override
    public String describe() {
        return delegate.describe() + ", injected faults " + faults.values();
    }
}
