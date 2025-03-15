FROM node:22-slim
ARG FIREBASE_VERSION=13.33.0
ENV HOME=/home/node
EXPOSE 4000
EXPOSE 5001
EXPOSE 9090
EXPOSE 8085
EXPOSE 9099
EXPOSE 9199
RUN apt-get update && apt-get install -y autoconf g++ libtool make openjdk-17-jre-headless python3 && \
    npm install -g firebase-tools@${FIREBASE_VERSION} typescript && \
    npm cache clean --force
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
