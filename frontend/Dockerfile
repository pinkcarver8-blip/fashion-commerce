# 고객 앱(store)·관리자 앱(admin)을 빌드해 Nginx 하나로 서빙한다(ADR-009). 이미지는 판 번호 + 지문으로 고정(A5-12).
FROM node:24.22.3-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
WORKDIR /src
# pnpm은 package.json의 packageManager(판 번호 + sha512 지문)대로 Corepack이 받아 확인한다.
RUN corepack enable
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY store/package.json store/
COPY admin/package.json admin/
COPY packages/ui/package.json packages/ui/
COPY packages/api-client/package.json packages/api-client/
COPY packages/tokens/package.json packages/tokens/
# 잠금 파일과 다르면 설치하지 않는다(A5-12 고정 설치).
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.31.0-alpine@sha256:2f07d83bf561b506400dc183b1b2003803e39efbd22451f848adaba14d28c7c7
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/store/dist /usr/share/nginx/store
COPY --from=build /src/admin/dist /usr/share/nginx/admin
EXPOSE 3000 3001
