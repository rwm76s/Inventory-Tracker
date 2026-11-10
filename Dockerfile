# ---- Build stage: full JDK + Maven wrapper ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy only build descriptors first so the dependency layer is cached
# and only re-downloads when the pom changes, not on every code edit.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q package -DskipTests

# ---- Runtime stage: JRE only, so the image is smaller ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user: a compromised app shouldn't own the container.
RUN useradd --system --no-create-home appuser
USER appuser

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]