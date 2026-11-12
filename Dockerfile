# syntax=docker/dockerfile:1.7
FROM node:24.16.0-alpine AS dependencies
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM dependencies AS development
COPY frontend/ ./
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]

FROM dependencies AS build
COPY frontend/ ./
RUN npm run check && npm run build

FROM node:24.16.0-alpine AS frontend
WORKDIR /app
ENV NODE_ENV=production PORT=8080
COPY --from=build /app/dist ./dist
COPY docker/static-server.mjs ./server.mjs
USER node
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8080/health/frontend').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "server.mjs"]

# Production: one Spring Boot image serving the API and the compiled frontend.
FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle ./
COPY gradle/ gradle/
COPY backend/ backend/
COPY --from=build /app/dist/ backend/auth-app/src/main/resources/static/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :backend:auth-app:bootJar --no-daemon \
  && cp backend/auth-app/build/libs/auth-app-*[!n].jar /workspace/app.jar

FROM eclipse-temurin:25-jre AS app
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home tracedesk
COPY --from=backend-build --chown=tracedesk:tracedesk /workspace/app.jar app.jar
USER tracedesk
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar /app/app.jar"]
