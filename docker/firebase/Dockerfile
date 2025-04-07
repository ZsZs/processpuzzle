FROM node:22-alpine
ARG FIREBASE_VERSION=13.33.0
RUN apk --no-cache add openjdk11-jre bash curl openssl gettext nano nginx sudo && \
    npm cache clean --force && \
    npm i -g firebase-tools@$FIREBASE_VERSION
COPY docker/firebase/nginx.conf /etc/nginx/
COPY docker/firebase/serve.sh /usr/bin/
RUN sed -i "s/\r$//" /usr/bin/serve.sh
RUN chmod +x /usr/bin/serve.sh
WORKDIR /srv/firebase
COPY .firebaserc .
COPY firebase.json .
COPY firestore.indexes.json .
COPY firestore.rules .
COPY storage.rules .
COPY dist/apps/processpuzzle-testbed ./dist/apps/processpuzzle-testbed
COPY apps/firebase-functions ./apps/firebase-functions
COPY apps/processpuzzle-testbed/data ./data
EXPOSE 4000
EXPOSE 4600
EXPOSE 5001
EXPOSE 6002
EXPOSE 9090
EXPOSE 8085
EXPOSE 9099
EXPOSE 9199
ENTRYPOINT ["/usr/bin/serve.sh"]
