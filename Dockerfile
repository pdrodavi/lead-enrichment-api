# === Build stage ===
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -DskipTests
COPY src ./src
RUN mvn package -DskipTests -Dmaven.test.skip=true -B

# === Runtime stage ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Application configuration (with sensible defaults)
ARG DB_URL=
ARG DB_USERNAME=
ARG DB_PASSWORD=
ARG API_KEY=
ARG ENCRYPTION_SECRET=
ARG PORT=8081

ENV DB_URL=$DB_URL
ENV DB_USERNAME=$DB_USERNAME
ENV DB_PASSWORD=$DB_PASSWORD
ENV API_KEY=$API_KEY
ENV ENCRYPTION_SECRET=$ENCRYPTION_SECRET
ENV PORT=8081

COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=8081"]
