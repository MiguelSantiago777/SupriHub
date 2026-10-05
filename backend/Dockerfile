# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && \
    java -Djarmode=tools -jar target/suprihub-backend-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN groupadd --system suprihub && useradd --system --gid suprihub --home /app suprihub && \
    mkdir -p /app/data/files && chown -R suprihub:suprihub /app
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER suprihub
ENV TZ=America/Sao_Paulo \
    STORAGE_PATH=/app/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo -Dfile.encoding=UTF-8"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
