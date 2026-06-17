FROM node:22-alpine
ARG FIREBASE_VERSION=15.20.0
RUN apk --no-cache add openjdk11-jre bash curl && \
    npm cache clean --force && \
    npm i -g firebase-tools@$FIREBASE_VERSION

WORKDIR /workspace

# Firebase root config (paths inside firebase.json are resolved relative to here)
COPY .firebaserc firebase.json firestore.indexes.json firestore.rules storage.rules ./

# Emulator import data (shared with native dev)
COPY tools/firebase/data ./tools/firebase/data

# Single source of truth for mock data
COPY tools/mock-backend/db.json ./tools/mock-backend/db.json

# Functions sources + build (produces lib/index.js and syncs db.json)
COPY tools/firebase/functions ./tools/firebase/functions
RUN npm --prefix tools/firebase/functions install && \
    npm --prefix tools/firebase/functions run build

COPY tools/docker/firebase/serve.sh /usr/bin/
RUN sed -i "s/\r$//" /usr/bin/serve.sh && chmod +x /usr/bin/serve.sh

# Emulator ports (per firebase.json)
EXPOSE 4000 4400 4600 5001 8081 8085 9099 9199

ENTRYPOINT ["/usr/bin/serve.sh"]
