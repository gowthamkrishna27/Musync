# =========================================================================
# Tier 1: Build Workspace Stage
# =========================================================================
FROM node:20-slim AS builder

WORKDIR /app

# Copy dependency configs first to leverage Docker layer caching
COPY package*.json tsconfig.json ./

# Install all development dependencies to compile TypeScript files
RUN npm ci

# Copy the rest of the application files
COPY . .

# Compile TypeScript down to optimized production vanilla JavaScript code
RUN npm run build

# =========================================================================
# Tier 2: Final Light Runtime Runner
# =========================================================================
FROM node:20-slim AS runner

WORKDIR /app

# Install system dependencies (Python3 & FFmpeg) required by the yt-dlp StreamManager
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy package configurations and install production dependencies only
COPY package*.json ./
RUN npm ci --only=production

# Pull compiled javascript production files from the builder tier stage
COPY --from=builder /app/dist ./dist

# Hugging Face Spaces requires your container service to run on port 7860
ENV PORT=7860
EXPOSE 7860

# Start your microservice instance 
CMD ["node", "dist/server.js"]
