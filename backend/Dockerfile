FROM node:20-alpine

WORKDIR /app

# Install Python 3, pip, ffmpeg, ca-certificates, curl, and create python symlink
RUN apk add --no-cache python3 py3-pip ffmpeg ca-certificates curl && \
    ln -sf /usr/bin/python3 /usr/bin/python

# Install latest standalone official yt-dlp binary + pip package
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    pip install --no-cache-dir --break-system-packages -U yt-dlp

# Copy package configurations and install dependencies
COPY package*.json ./
RUN npm install

# Copy project files
COPY tsconfig.json ./
COPY scripts ./scripts
COPY src ./src

EXPOSE 5000

ENV PORT=5000
ENV NODE_ENV=production
ENV PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"

CMD ["npx", "tsx", "src/server.ts"]
