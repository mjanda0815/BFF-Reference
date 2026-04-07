FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B -e -ntp dependency:go-offline
COPY src ./src
RUN mvn -q -B -e -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/bff.jar app.jar
USER app
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
