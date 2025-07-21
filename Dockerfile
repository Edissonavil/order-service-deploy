# ──────────────── BUILD STAGE ──────────────────
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

RUN apk add --no-cache curl

# 1) Copiamos el pom padre (raíz) y los pom de los módulos
COPY pom.xml .                                   
COPY order-service/pom.xml order-service/
COPY events/pom.xml events/

# 2) Descargamos dependencias de los submódulos necesarios
#    -pl order-service        ► compila SÓLO ese módulo…
#    -am                      ► …y además (also-make) todo lo que él dependa, → events
RUN mvn -B -pl order-service -am dependency:go-offline

# 3) Copiamos el código fuente de los módulos
COPY order-service/ order-service/
COPY events/ events/

# 4) Construimos el jar de order-service (+ events) SIN tests
RUN mvn -B -pl order-service -am clean package -DskipTests


# ──────────────── RUNTIME STAGE ────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos ÚNICAMENTE el jar generado de order-service
COPY --from=build /app/order-service/target/order-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=prod"]
