FROM node:20-alpine

WORKDIR /app

# Install Python 3, pip, and ffmpeg
RUN apk add --no-cache python3 py3-pip ffmpeg

# Install yt-dlp for direct YouTube CDN audio resolution
RUN pip install --no-cache-dir --break-system-packages yt-dlp

# Copy dependency specifications
COPY package*.json ./
RUN npm install

# Copy source code and scripts
COPY tsconfig.json ./
COPY server.ts ./
COPY stream_resolver.py ./

EXPOSE 5000

ENV PORT=5000

CMD ["npx", "tsx", "server.ts"]
