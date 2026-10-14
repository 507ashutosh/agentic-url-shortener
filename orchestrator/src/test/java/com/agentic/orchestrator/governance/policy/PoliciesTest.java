package com.agentic.orchestrator.governance.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentic.orchestrator.governance.policy.PolicyFinding.Severity;
import com.agentic.orchestrator.run.ChangedFile;

class PoliciesTest {

    private static ChangedFile changed(String path, String before, String after) {
        return new ChangedFile(path, before, after);
    }

    // --- security/secret-scan ---

    @Test
    void secretScanBlocksAddedCredentialsAndMasksThem() {
        List<PolicyFinding> findings = new SecretScanPolicy().evaluate(List.of(
                changed("docs/a.md", "intro\n", "intro\napi_key = \"AKIAABCDEFGHIJKLMNOP\"\n"),
                changed("src/B.java", null, "String k = \"sk-ant-api03-abcdefghijklmnopqrstuvwxyz\";\n")));

        assertThat(findings).hasSize(2).allMatch(f -> f.severity() == Severity.BLOCK);
        assertThat(findings.get(0).evidence()).startsWith("docs/a.md: api_key = \"A").endsWith("****")
                .doesNotContain("AKIAABCDEFGHIJKLMNOP");
    }

    @Test
    void secretScanIgnoresLinesThatWereAlreadyThereAndOrdinaryCode() {
        String existing = "password = \"already-here-before\"\n";
        List<PolicyFinding> findings = new SecretScanPolicy().evaluate(List.of(
                changed("app.properties", existing, existing + "server.port=8080\n"),
                changed("src/C.java", null, "String password = request.password();\n")));

        assertThat(findings).isEmpty();
    }

    // --- change-control/db-migration ---

    @Test
    void schemaChangeRequiresApprovalAndShowsTheChangedLines() {
        List<PolicyFinding> findings = new SchemaChangePolicy().evaluate(List.of(
                changed("shortener/src/main/resources/schema.sql", "a INT,\n", "a INT,\nexpires_at TIMESTAMP,\n"),
                changed("shortener/src/main/java/A.java", "x", "y")));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.REQUIRES_APPROVAL);
            assertThat(f.evidence()).contains("+ expires_at TIMESTAMP,");
        });
    }

    // --- compliance/test-integrity ---

    @Test
    void testIntegrityBlocksDeletedDisabledOrRemovedTests() {
        String twoTests = "@Test\nvoid a() {}\n@Test\nvoid b() {}\n";
        List<PolicyFinding> findings = new TestIntegrityPolicy().evaluate(List.of(
                changed("shortener/src/test/java/DeletedTest.java", twoTests, null),
                changed("shortener/src/test/java/DisabledTest.java", twoTests, "@Disabled\n" + twoTests),
                changed("shortener/src/test/java/ThinnedTest.java", twoTests, "@Test\nvoid a() {}\n")));

        assertThat(findings).extracting(PolicyFinding::reason).containsExactly(
                "existing test file deleted in shortener/src/test/java/DeletedTest.java",
                "test disabled with @Disabled in shortener/src/test/java/DisabledTest.java",
                "existing tests removed (2 → 1) in shortener/src/test/java/ThinnedTest.java");
    }

    @Test
    void testIntegrityAllowsNewAndExtendedTests() {
        List<PolicyFinding> findings = new TestIntegrityPolicy().evaluate(List.of(
                changed("shortener/src/test/java/NewTest.java", null, "@Test\nvoid a() {}\n"),
                changed("shortener/src/test/java/OldTest.java", "@Test\nvoid a() {}\n",
                        "@Test\nvoid a() {}\n@Test\nvoid b() {}\n")));

        assertThat(findings).isEmpty();
    }

    // --- compliance/dependency-change and change-control/blast-radius ---

    @Test
    void dependencyChangeAndLargeChangesRequireApproval() {
        assertThat(new DependencyChangePolicy().evaluate(List.of(changed("shortener/pom.xml", "a", "b"))))
                .singleElement().extracting(PolicyFinding::severity).isEqualTo(Severity.REQUIRES_APPROVAL);

        List<ChangedFile> many = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> changed("f" + i, null, "x")).toList();
        assertThat(new BlastRadiusPolicy(3).evaluate(many)).hasSize(1);
        assertThat(new BlastRadiusPolicy(4).evaluate(many)).isEmpty();
    }
}
