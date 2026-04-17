# syntax=docker/dockerfile:1.7
# Requires next.config.ts to have: output: 'standalone'

# ============================================================================
# STAGE 1: Install all deps (needed for build)
# ============================================================================
FROM node:22-alpine AS deps

RUN apk add --no-cache libc6-compat
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# ============================================================================
# STAGE 2: Build Next.js app (standalone output)
# ============================================================================
FROM node:22-alpine AS builder

RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_* vars are inlined at build time, not runtime
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

# Disable Next.js telemetry in CI/build
ENV NEXT_TELEMETRY_DISABLED=1

RUN pnpm run build

# ============================================================================
# STAGE 3: Minimal runtime (standalone mode = no node_modules needed)
# ============================================================================
FROM node:22-alpine AS runner

RUN apk add --no-cache tini curl && \
    rm -rf /var/cache/apk/*

# Non-root user (CIS Docker Benchmark)
RUN addgroup -g 1654 -S appgroup && \
    adduser -u 1654 -S appuser -G appgroup

WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Copy only what standalone output needs
COPY --from=builder --chown=1654:1654 /app/public ./public
COPY --from=builder --chown=1654:1654 /app/.next/standalone ./
COPY --from=builder --chown=1654:1654 /app/.next/static ./.next/static

USER 1654:1654

EXPOSE 3000

LABEL org.opencontainers.image.title="Vietnam Traffic Law Chatbot — Frontend" \
  org.opencontainers.image.description="Next.js chat and admin interface for the Vietnam traffic law AI chatbot" \
  org.opencontainers.image.vendor="kl3inIT" \
  org.opencontainers.image.authors="kl3inIT" \
  org.opencontainers.image.source="https://git.dth.com.vn/jmix-team/research/traffic-law/frontend/core-frontend" \
  org.opencontainers.image.version="1.0.0" \
  org.opencontainers.image.licenses="MIT"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:3000/ || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
