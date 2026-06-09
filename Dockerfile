FROM gradle:8.10.2-jdk21-alpine AS build

WORKDIR /app

COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies

COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/build/libs/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

