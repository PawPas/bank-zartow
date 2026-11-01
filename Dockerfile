# syntax=docker/dockerfile:1

# Single production service: Spring Boot serves the built React frontend.
# Three stages keep source code and build tools out of the final image.

# 1. Build the React frontend.
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot application, bundling the frontend as static resources.
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
# The pom copies ../frontend/dist into the jar's static resources (see pom.xml).
COPY --from=frontend /frontend/dist/ /frontend/dist/
RUN ./mvnw -B -DskipTests package

# 3. Lightweight runtime: JRE and the application jar only. No sources, no Maven,
# no Node, no npm.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# JVM memory limit, set via an environment variable and overridable at deploy
# time (for example to fit a Railway memory cap).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
COPY --from=backend /backend/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
