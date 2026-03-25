# Bước 1: Dùng Maven để build code thành file .jar
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Bước 2: Chạy file .jar bằng Java 17
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file .jar từ bước 1 sang bước 2 (Render sẽ tự động chạy cái này)
COPY --from=build /app/target/coordinator-server-0.0.1-SNAPSHOT.jar app.jar

# Mở cổng 8080 để Render nhận diện được Web Service
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]