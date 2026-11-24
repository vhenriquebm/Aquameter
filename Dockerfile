# Etapa 1: build com Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagem de execução
FROM eclipse-temurin:21-jre
ENV TZ=America/Sao_Paulo
WORKDIR /app
COPY --from=build /app/target/aquameter.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-jar", "app.jar"]
