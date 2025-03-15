FROM node:23-alpine
ARG FIREBASE_VERSION=13.33.0
RUN apk --no-cache add openjdk11-jre bash curl openssl gettext nano nginx sudo && \
    npm cache clean --force && \
    npm i -g firebase-tools@$FIREBASE_VERSION
COPY ./docker/firebase/nginx.conf /etc/nginx/
COPY ./docker/firebase/serve.sh /usr/bin/
RUN chmod +x /usr/bin/serve.sh
WORKDIR /srv/firebase
COPY ./docker/firebase/.firebaserc .
COPY ./docker/firebase/firebase.json .
COPY ./docker/firebase/firestore.indexes.json .
COPY ./docker/firebase/firestore.rules .
COPY ./docker/firebase/storage.rules .
COPY ./docker/firebase/functions .
ENTRYPOINT ["/usr/bin/serve.sh"]
