# Contributing to Musync

Thank you for your interest in contributing to Musync!

---

## Code of Conduct & Principles

* **Keep it grounded**: Any feature or optimization must preserve existing streaming stability, audio focus, and caching guarantees.
* **No unverified dependencies**: Do not introduce heavy dependencies without prior design discussion.
* **Performance first**: Avoid blocking operations on the main thread in Android or on the Node.js event loop.

---

## Development Workflow

### 1. Branching Strategy
* Create feature or bugfix branches off the main branch:
  ```bash
  git checkout -b feature/my-new-feature
  ```
  or
  ```bash
  git checkout -b fix/resolve-stream-issue
  ```

### 2. Android Code Guidelines
* Maintain Kotlin code style and clean separation between UI (Compose), ViewModel, and Domain/Data layers.
* Keep playback-specific logic inside `com.musync.app.playback`.
* Ensure coroutines use appropriate dispatchers (`Dispatchers.IO` for disk/network, `Dispatchers.Main` for UI/MediaController).

### 3. Backend Code Guidelines
* Maintain TypeScript strict typing (`strict: true` in `tsconfig.json`).
* Ensure any long-running or CPU-intensive task is properly handled asynchronously without starving the Node event loop.
* Preserve rate limiting and process semaphore protections.

### 4. Running Verification Checks Before Submitting PR

Before opening a pull request, run all verification builds:

#### Android Build & Test
```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

#### Backend Build Check
```bash
cd backend
npm run build
```

---

## Submitting Pull Requests

1. Commit your changes with clear, descriptive commit messages.
2. Push your branch to GitHub and open a Pull Request.
3. Provide a clear summary of the changes made, the motivation, and test steps performed.
