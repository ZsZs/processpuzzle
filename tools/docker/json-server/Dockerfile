FROM node:latest
WORKDIR /usr/src/mock-backend

COPY ./tools/mock-backend/db.json .
COPY ./tools/mock-backend/cert.pem .
COPY ./tools/mock-backend/key.pem .
COPY ./tools/mock-backend/package.json .
RUN npm install

EXPOSE 3000

CMD ["npm", "run", "start"]
