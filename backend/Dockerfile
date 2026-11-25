# 백엔드 이미지. 이미지는 판 번호 + 지문으로 고정(A5-12). Gradle은 wrapper의 배포본 지문과 잠금 파일대로 받는다.
FROM eclipse-temurin:24.0.2_12-jdk-noble@sha256:dacac8e9a0df0d2bd24e702b4431132875c249930b70555ebd7ca285b5bee684 AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.lockfile buildscript-gradle.lockfile lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
# 시험은 CI가 돌린다(R0-08). 이미지 빌드에서는 실행 파일만 만든다.
RUN ./gradlew --no-daemon --quiet bootJar -x test

FROM eclipse-temurin:24.0.2_12-jre-noble@sha256:b416d02335e702b0403ff280de9475a3348e29382285969c9d4e17862ce632e7
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/build/libs/commerce-0.0.1-SNAPSHOT.jar app.jar
USER app
# 시간대는 UTC로 고정한다(ADR-010). 확인 시험은 R0-08.
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
