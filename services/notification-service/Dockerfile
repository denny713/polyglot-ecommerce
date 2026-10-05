FROM node:22-alpine

WORKDIR /app

# Dependencies first so this layer is only rebuilt when the lockfile changes.
# Dev dependencies (jest, nodemon) stay out of the image.
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile --production && yarn cache clean

COPY src ./src

ENV NODE_ENV=production
USER node
EXPOSE 7160

# No .env is baked into the image: configuration comes from the environment,
# which is what compose injects.
CMD ["node", "src/app.js"]
