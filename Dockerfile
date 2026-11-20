# syntax=docker/dockerfile:1

# Stage 1: build the React frontend.
FROM node:22.22.2-bookworm-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: build the Spring Boot jar with the frontend bundled as static resources.
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q dependency:go-offline
COPY src/ src/
COPY --from=frontend /frontend/dist/ src/main/resources/static/
RUN ./mvnw -q package -DskipTests

# Stage 3: runtime image.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar

# Run with the prod profile by default so RabbitMQ/Redis and the quiz grading listener are configured.
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
