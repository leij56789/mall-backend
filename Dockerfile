FROM eclipse-temurin:17.0.13_11-jre-ubi9-minimal
LABEL maintainer="leij56789"
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]