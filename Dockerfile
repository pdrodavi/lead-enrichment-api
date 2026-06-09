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
ARG API_KEY=b6vxAgj5KG5HPGCKlQQ7
ARG ENCRYPTION_SECRET=f44sGktPn25aHIuTfi9KbIwNnh8qO0xdbn+KmwwePz8=
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
