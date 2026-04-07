# Multi-stage build for the Angular frontend.
#
# Stage 1: install dependencies and produce a production build.
# Stage 2: serve the static bundle behind nginx.

# ---------- build stage ----------
FROM node:20-alpine AS build

WORKDIR /workspace

# Install dependencies first so the layer is cached when only sources change.
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

# Copy the rest of the project and build.
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY src ./src
RUN npx ng build --configuration production

# ---------- runtime stage ----------
FROM nginx:1.27-alpine AS runtime

# Replace the default site config with our SPA-aware one.
COPY nginx.conf /etc/nginx/nginx.conf

# Angular 17+ writes to dist/<project>/browser.
COPY --from=build /workspace/dist/bff-frontend/browser /usr/share/nginx/html

# Drop privileges where possible. The official nginx image still needs
# root to bind port 80 and to write its pid file, but we keep the
# attack surface small by removing the default html assets.
RUN rm -f /usr/share/nginx/html/index.nginx-debian.html || true

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
