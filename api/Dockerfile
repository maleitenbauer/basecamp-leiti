# Build context is the REPO ROOT (see docker-compose.yml's `context: .`), not api/, because this Dockerfile also
# needs the sibling analysis-parser/ directory (a separate Go toolchain, same "one folder per toolchain" pattern
# as api/ and web/ — see README's "Repository structure").

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY api/ .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# A static binary: no Go runtime needed in the final image, just the compiled executable (same "ship only the
# build output" idea as the two stages above). CGO_ENABLED=0 avoids any dependency on the base image's libc.
FROM golang:1.24-bookworm AS parser-build
WORKDIR /src
COPY analysis-parser/ .
RUN go mod tidy && CGO_ENABLED=0 go build -o /out/analysis-parser .

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home app \
    && mkdir -p /data/demos \
    && chown app:app /data/demos
COPY --from=build /src/build/libs/*.jar app.jar
COPY --from=parser-build /out/analysis-parser /app/analysis-parser
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
