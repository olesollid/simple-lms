FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy the jar built by the GitLab CI 'package' stage
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]