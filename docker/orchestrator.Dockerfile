# The agentic orchestrator, ready to run the scenarios in mock mode (no API key needed).
#   docker build -f docker/orchestrator.Dockerfile -t agentic-orchestrator .
#   docker run --rm -it -v "$PWD/runs:/workspace/runs" agentic-orchestrator --scenario brownfield
# Add --auto-approve to skip the approval prompts, or -e ANTHROPIC_API_KEY ... --live for real agents.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shortener shortener
COPY orchestrator orchestrator
RUN ./mvnw -B -q package -DskipTests

# A full JDK at runtime: the validate stage compiles and tests the changed shortener.
# git: the orchestrator snapshots the workspace with it, for rollback and the release patch.
FROM eclipse-temurin:25-jdk
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home agent \
    && mkdir -p /workspace/runs && chown -R agent /workspace
WORKDIR /workspace
COPY --chown=agent mvnw mvnw.cmd pom.xml ./
COPY --chown=agent .mvn .mvn
COPY --chown=agent shortener/pom.xml shortener/pom.xml
COPY --chown=agent shortener/src shortener/src
COPY --chown=agent scenarios scenarios
COPY --from=build /src/orchestrator/target/orchestrator.jar /opt/orchestrator.jar
USER agent
# Run the shortener's build once, so Maven and every library the validate stage needs are already cached.
RUN ./mvnw -B -q -f shortener/pom.xml test && rm -rf shortener/target
ENTRYPOINT ["java", "-jar", "/opt/orchestrator.jar"]
CMD ["--scenario", "brownfield"]
