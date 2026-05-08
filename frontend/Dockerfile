# Multi-stage build for the Angular frontend.
#
# Stage 1: install dependencies and produce a production build.
# Stage 2: serve the static bundle behind nginx.

# ---------- build stage ----------
FROM node:26-alpine AS build

WORKDIR /workspace

# Install dependencies first so the layer is cached when only sources change.
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

# Copy the rest of the project and build.
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY src ./src
RUN npx ng build --configuration production

# ---------- runtime stage ----------
FROM nginx:1.29-alpine AS runtime

# Replace the default site config with our SPA-aware one.
COPY nginx.conf /etc/nginx/nginx.conf

# Angular 17+ writes to dist/<project>/browser.
COPY --from=build /workspace/dist/bff-frontend/browser /usr/share/nginx/html

# Drop privileges where possible. The official nginx image still needs
# root to bind port 80 and to write its pid file, but we keep the
# attack surface small by removing the default html assets.
RUN rm -f /usr/share/nginx/html/index.nginx-debian.html || true

EXPOSE 80

# Pin the probe to 127.0.0.1 so it never tries IPv6: nginx in this image binds
# only to 0.0.0.0:80, but BusyBox wget would otherwise resolve "localhost" to
# [::1]:80 and the healthcheck would fail "Connection refused" on a perfectly
# healthy nginx (Docker would mark the container unhealthy even though it
# serves HTTP 200 fine on the host port).
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
