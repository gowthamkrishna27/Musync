FROM node:20-alpine

WORKDIR /app

# Install Python 3, pip, ffmpeg, ca-certificates, and create python symlink
RUN apk add --no-cache python3 py3-pip ffmpeg ca-certificates && \
    ln -sf /usr/bin/python3 /usr/bin/python

# Install yt-dlp for direct YouTube audio extraction
RUN pip install --no-cache-dir --break-system-packages yt-dlp

# Copy package configurations and install dependencies
COPY package*.json ./
RUN npm install

# Copy project files
COPY tsconfig.json ./
COPY server.ts ./
COPY stream_resolver.py ./
COPY src ./src

EXPOSE 5000

ENV PORT=5000
ENV NODE_ENV=production

CMD ["npx", "tsx", "server.ts"]
