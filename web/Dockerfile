# syntax=docker/dockerfile:1.10

# ---- Build stage ---------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Runtime stage -------------------------------------------------------------------------
# nginx-unprivileged runs as uid 101 and listens on an unprivileged port.
FROM nginxinc/nginx-unprivileged:1.31-alpine AS runtime

ENV API_UPSTREAM=http://api:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

USER 101

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
