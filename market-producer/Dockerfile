FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper first (Docker cache layer)
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy parent POM and module sources
COPY pom.xml .
COPY market-common/pom.xml market-common/pom.xml
COPY market-common/src market-common/src
COPY market-producer/pom.xml market-producer/pom.xml
COPY market-producer/src market-producer/src
# Stub pom.xml for sibling modules — Maven reactor reads parent pom.xml
# and requires ALL declared <module> directories to exist, even when using -pl
COPY market-consumer/pom.xml market-consumer/pom.xml
COPY market-api/pom.xml market-api/pom.xml

# Build only the producer and its dependency (market-common)
RUN ./mvnw -pl market-common,market-producer -am package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/market-producer/target/*.jar app.jar

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+OptimizeStringConcat", \
  "-jar", "/app/app.jar"]
