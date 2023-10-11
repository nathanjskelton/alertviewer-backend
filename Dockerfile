FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
ARG JAR_FILE
COPY target/alertviewer-backend-1.0-SNAPSHOT.jar app.jar
COPY application.properties ./
COPY keystore.jks ./
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app.jar"]
