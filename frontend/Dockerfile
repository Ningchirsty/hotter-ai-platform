FROM node:22-alpine AS build

ARG BUILD_SHA=unknown

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable && corepack prepare pnpm@10.34.5 --activate
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build:prod
RUN printf '{"gitSha":"%s"}\n' "$BUILD_SHA" > /app/dist/version.json

FROM nginx:1.27-alpine
ENV LISTEN_PORT=80 \
    BACKEND_ORIGIN=http://127.0.0.1:18082 \
    NGINX_ENVSUBST_FILTER='^(LISTEN_PORT|BACKEND_ORIGIN)$'
COPY nginx.production.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --retries=12 CMD wget -q -O /dev/null "http://127.0.0.1:${LISTEN_PORT}/version.json" || exit 1
