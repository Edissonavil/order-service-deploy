# ---------- Build Stage ----------
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar el pom.xml principal de order-service
COPY order-service/pom.xml .

# Copiar el código fuente completo del microservicio (order-service + events)
COPY order-service/src ./src
COPY events ./events

# Compilar sin ejecutar tests
RUN mvn -B package -DskipTests

# ---------- Runtime Stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el JAR generado desde el contenedor de build
COPY --from=build /app/target/*.jar app.jar

# Cambia el puerto según tu servicio
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]

