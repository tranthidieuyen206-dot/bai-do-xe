# Buoc 1: Build code
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Buoc 2: Chay ung dung
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/gate-server-5-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]