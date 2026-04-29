# ══════════════════════════════════════════════════════════════
# BizSupport — Multi-stage Docker build
# ══════════════════════════════════════════════════════════════

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd -r bizsupport && useradd -r -g bizsupport bizsupport

COPY --from=build /app/target/*.jar app.jar

RUN chown -R bizsupport:bizsupport /app
USER bizsupport

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/login || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
