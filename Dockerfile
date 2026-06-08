# === Build stage ===
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -DskipTests
COPY src ./src
RUN mvn package -DskipTests -Dmaven.test.skip=true -B

# === Runtime stage ===
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Application configuration (with sensible defaults)
ARG DB_URL=jdbc:postgresql://localhost:5433/postgres
ARG DB_USERNAME=postgres
ARG DB_PASSWORD=pgsqldev
ARG REDIS_HOST=localhost
ARG REDIS_PORT=6379
ARG API_KEY=dev-key-change-in-production
ARG ENCRYPTION_SECRET=CHANGE_ME_32_BYTE_SECRET_KEY!
ARG PORT=8081

ENV DB_URL=$DB_URL
ENV DB_USERNAME=$DB_USERNAME
ENV DB_PASSWORD=$DB_PASSWORD
ENV REDIS_HOST=$REDIS_HOST
ENV REDIS_PORT=$REDIS_PORT
ENV API_KEY=$API_KEY
ENV ENCRYPTION_SECRET=$ENCRYPTION_SECRET
ENV PORT=$PORT

COPY --from=builder /build/target/*.jar app.jar
EXPOSE $PORT
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=$PORT"]
