# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Baixa as dependências em uma camada separada para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /app/target/ia-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
