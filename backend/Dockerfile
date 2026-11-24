# 백엔드 이미지. 이미지는 판 번호 + 지문으로 고정(A5-12). Gradle은 wrapper의 배포본 지문과 잠금 파일대로 받는다.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:57424b1d8eca9c4cf8a4fe226207027911f08d6758628891bf59fcfb53777029 AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.lockfile buildscript-gradle.lockfile lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
# 시험은 CI가 돌린다(R0-08). 이미지 빌드에서는 실행 파일만 만든다.
RUN ./gradlew --no-daemon --quiet bootJar -x test

FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:cd35bb33859641c5374415826a5951845104093eaa401cbe9d3d9023444f1c13
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/build/libs/commerce-0.0.1-SNAPSHOT.jar app.jar
USER app
# 시간대는 UTC로 고정한다(ADR-010). 확인 시험은 R0-08.
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
