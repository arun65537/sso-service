FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/sso-service-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
