# syntax=docker/dockerfile:1

# Static files are platform independent, so multi-arch builds run npm once on the build machine.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

FROM nginxinc/nginx-unprivileged:stable-alpine
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/geo-tracker/browser /usr/share/nginx/html
EXPOSE 8080
