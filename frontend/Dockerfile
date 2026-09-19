# ==============================================================================
# Food Risk Analysis - Frontend Production Dockerfile
# Multi-stage build: Node.js Vite build -> Minimal Nginx runtime
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build static assets with Node.js
# ------------------------------------------------------------------------------
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Serve static production bundle with Nginx Alpine
# ------------------------------------------------------------------------------
FROM nginx:1.27-alpine

# Copy custom production nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled SPA bundle from builder
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

# Health check verifying web server responsiveness
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
