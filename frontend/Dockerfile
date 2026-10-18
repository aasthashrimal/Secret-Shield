# syntax=docker/dockerfile:1
FROM node:24-alpine3.24 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig.json vite.config.ts ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM nginx:1.31.7-alpine3.24
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
ENV BACKEND_ORIGIN=http://backend:8080 \
    NGINX_ENVSUBST_FILTER=BACKEND_ORIGIN
EXPOSE 80
