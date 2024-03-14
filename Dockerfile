FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
ARG VERSION
COPY target/alertviewer-backend-$VERSION.jar app.jar
COPY src/main/resources/application.properties ./
COPY keystore.jks ./
EXPOSE 8085
ENTRYPOINT java $JAVA_OPTS -jar /app.jar
