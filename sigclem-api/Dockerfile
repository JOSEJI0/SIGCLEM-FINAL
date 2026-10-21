# Etapa 1: compilación
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x ./mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q clean package -DskipTests

# Etapa 2: ejecución
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Tesseract (OCR en español) para cotejar la descripción de comprobantes escaneados o fotografiados,
# y fuentes del sistema para que PDFBox dibuje los PDF (lectura del QR y documentos generados).
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-spa fontconfig ttf-dejavu
RUN addgroup -S sigclem && adduser -S sigclem -G sigclem \
    && mkdir -p /srv/sigclem/archivos && chown -R sigclem:sigclem /srv/sigclem
COPY --from=compilacion /app/target/*.jar app.jar
USER sigclem
ENV TZ=America/Mexico_City \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/salud > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
