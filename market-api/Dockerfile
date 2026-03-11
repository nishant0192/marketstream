FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

COPY pom.xml .
COPY market-common/pom.xml market-common/pom.xml
COPY market-common/src market-common/src
COPY market-api/pom.xml market-api/pom.xml
COPY market-api/src market-api/src
# Stub pom.xml for sibling modules — Maven reactor requires all declared modules
COPY market-producer/pom.xml market-producer/pom.xml
COPY market-consumer/pom.xml market-consumer/pom.xml

RUN ./mvnw -pl market-common,market-api -am package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/market-api/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+OptimizeStringConcat", \
  "-jar", "/app/app.jar"]
