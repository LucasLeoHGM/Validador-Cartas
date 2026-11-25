# Etapa 1: compila o projeto
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Etapa 2: imagem final com Java + Tesseract (portugues) + Python/OpenCV
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr tesseract-ocr-eng tesseract-ocr-por \
        python3 python3-opencv python3-numpy python3-pip poppler-utils \
    && pip3 install --no-cache-dir --break-system-packages pdf2image \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/validador-documentos.jar app.jar
COPY scripts ./scripts
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
