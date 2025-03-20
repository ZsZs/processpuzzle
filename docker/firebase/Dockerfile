FROM node:22-alpine
ARG FIREBASE_VERSION=13.33.0
RUN apk --no-cache add openjdk11-jre bash curl openssl gettext nano nginx sudo && \
    npm cache clean --force && \
    npm i -g firebase-tools@$FIREBASE_VERSION
COPY docker/firebase/nginx.conf /etc/nginx/
COPY docker/firebase/serve.sh /usr/bin/
RUN chmod +x /usr/bin/serve.sh
WORKDIR /srv/firebase
COPY docker/firebase/.firebaserc .
COPY docker/firebase/firebase.json .
COPY docker/firebase/firestore.indexes.json .
COPY docker/firebase/firestore.rules .
COPY docker/firebase/storage.rules .
COPY docker/firebase/functions ./functions
EXPOSE 4000
EXPOSE 5001
EXPOSE 9090
EXPOSE 8085
EXPOSE 9099
EXPOSE 9199
ENTRYPOINT ["/usr/bin/serve.sh"]
