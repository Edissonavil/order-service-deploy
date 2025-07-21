FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# Copia TODO el proyecto
COPY . .

# Compila todos los módulos, incluyendo order-service y events
RUN mvn -B clean package -DskipTests

# ---------- Runtime Stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia solo el jar del módulo que deseas ejecutar (aquí: order-service)
COPY --from=build /app/order-service/target/order-service-*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]

