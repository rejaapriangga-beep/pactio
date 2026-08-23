FROM node:22-alpine

WORKDIR /app
COPY package.json ./
COPY server.js ./

ENV NODE_ENV=production
ENV PORT=3030
EXPOSE 3030

VOLUME ["/app/storage"]
CMD ["node", "server.js"]
