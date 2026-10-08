# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests && \
    cp target/suprihub-backend-*.jar target/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN groupadd --system suprihub && useradd --system --gid suprihub --home /app suprihub && \
    mkdir -p /app/data/files && chown -R suprihub:suprihub /app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER suprihub
ENV TZ=America/Sao_Paulo \
    STORAGE_PATH=/app/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo -Dfile.encoding=UTF-8"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
