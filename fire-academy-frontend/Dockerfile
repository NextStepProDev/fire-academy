FROM node:26-alpine AS build
WORKDIR /app
ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
# Same reasoning as the backend image: the base ships whatever Alpine packages it was built with and
# is rebuilt on its own schedule, so fixes already published in the Alpine repo sit unapplied until
# then. The CVE gate in CI fails on anything fixable, and waiting for an upstream rebuild is not a
# plan. Nothing here is pinned by name, so tomorrow's advisory needs no edit to this line.
RUN apk upgrade --no-cache

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
