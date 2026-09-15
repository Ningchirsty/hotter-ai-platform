FROM node:22-alpine AS build

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable && corepack prepare pnpm@10.34.5 --activate
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build:prod

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --retries=12 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
