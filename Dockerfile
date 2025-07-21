# ──────────────── BUILD STAGE ──────────────────
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# 1) Copiamos el POM raíz y los POMs de los submódulos
COPY pom.xml .  
COPY order-service-deploy/pom.xml order-service-deploy/
COPY events/pom.xml events/

# 2) Pre-cache dependencias SOLO para order-service-deploy (y sus dependencias, e.g. events)
RUN mvn -B -pl order-service-deploy -am dependency:go-offline

# 3) Copiamos el código fuente de ambos módulos
COPY order-service-deploy/ order-service-deploy/
COPY events/ events/

# 4) Compilamos y empacamos order-service-deploy (+ events) sin tests
RUN mvn -B -pl order-service-deploy -am clean package -DskipTests


# ──────────────── RUNTIME STAGE ────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 5) Solo copiamos el jar resultante de order-service-deploy
COPY --from=build /app/order-service-deploy/target/order-service-deploy-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=prod"]
