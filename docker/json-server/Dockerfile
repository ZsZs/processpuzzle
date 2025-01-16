FROM node:latest
WORKDIR /usr/src/mock-backend

COPY ./mock-backend/db.json .
COPY ./mock-backend/cert.pem .
COPY ./mock-backend/key.pem .
COPY ./mock-backend/package.json .
RUN npm install

EXPOSE 3000

CMD ["npm", "run", "start"]
