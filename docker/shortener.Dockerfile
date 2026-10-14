# URL shortener service.
#   docker build -f docker/shortener.Dockerfile -t url-shortener .
#   docker run --rm -p 8080:8080 url-shortener
# Tests are not run here; they run in the normal build (./mvnw package).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shortener shortener
COPY orchestrator/pom.xml orchestrator/pom.xml
RUN ./mvnw -B -q -pl shortener package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home app
WORKDIR /app
COPY --from=build /src/shortener/target/shortener-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
