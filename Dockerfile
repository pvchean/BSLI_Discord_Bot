# Stage 1: Build the JAR
FROM gradle:8-jdk21 AS build
WORKDIR /app

# Copy source code and build files
COPY . .

# Build the fat jar (adjust the command if using Maven, e.g., mvn clean package)
RUN gradle clean build --no-daemon

# Stage 2: Minimal Runtime Environment
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy ONLY the built fat jar from the build stage
# Adjust the path below to match where your build tool outputs the fat jar
COPY --from=build /app/build/libs/*-all.jar ./bot.jar

# Run the bot
ENTRYPOINT ["java", "-jar", "bot.jar"]