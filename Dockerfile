FROM eclipse-temurin:21-jdk AS build

ARG MODULE
WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY buildSrc buildSrc
COPY proto proto
COPY common common
COPY gate gate
COPY player player
COPY world world
COPY global global
COPY gm gm
COPY stardust stardust
COPY tools tools
COPY config config

RUN ./gradlew ":${MODULE}:bootJar" --no-daemon \
    && jar_file="$(find "${MODULE}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "${jar_file}" \
    && cp "${jar_file}" /workspace/app.jar

FROM eclipse-temurin:21-jre

ARG MODULE
ENV APP_MODULE=${MODULE}
WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar
COPY deploy/docker/entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
