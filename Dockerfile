FROM openjdk:17-slim
WORKDIR /app
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml
COPY src src
RUN chmod +x ./mvnw
RUN ./mvnw install -DskipTests
RUN mv target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]