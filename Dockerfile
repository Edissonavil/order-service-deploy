# ─── BUILD STAGE ───
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# 1) Copiamos el POM raíz y los POMs de los submódulos
COPY pom.xml .
COPY order-service/pom.xml order-service/
COPY events/pom.xml events/

# 2) Pre-cache dependencias para order-service + events
RUN mvn -B -pl order-service -am dependency:go-offline

# 3) Copiamos el código fuente completo de esos módulos
COPY order-service/ order-service/
COPY events/ events/

# 4) Empaquetamos sólo el módulo order-service (y events) sin tests
RUN mvn -B -pl order-service -am clean package -DskipTests

# ─── RUNTIME STAGE ───
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 5) Copiamos el JAR generado de order-service
COPY --from=build /app/order-service/target/order-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=prod"]
