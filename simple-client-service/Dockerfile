FROM eclipse-temurin:17
WORKDIR /app
COPY target/*.jar app.jar
# Enable remote debugging on port 5005
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]

