# Environment Variables Reference

## Backend Environment Variables (`backend/.env`)

| Variable Name | Default Value | Example | Description |
| :--- | :---: | :---: | :--- |
| `PORT` | `5000` | `PORT=5000` | TCP port for the Express server |
| `NODE_ENV` | `development` | `NODE_ENV=production` | Node environment profile (`development` / `production`) |
| `REDIS_URL` | None | `REDIS_URL=redis://user:pass@host:6379` | Full connection URI for Redis L2 cache |
| `REDIS_PRIVATE_URL` | None | `REDIS_PRIVATE_URL=redis://...` | Alternative private Redis URI (used by Railway/Render) |
| `REDISHOST` / `REDIS_HOST` | None | `REDISHOST=127.0.0.1` | Redis host (if not using URI) |
| `REDISPORT` / `REDIS_PORT` | `6379` | `REDISPORT=6379` | Redis port |
| `REDISPASSWORD` / `REDIS_PASSWORD` | None | `REDISPASSWORD=secret` | Redis authentication password |

---

## Android Configuration (`local.properties` & App Settings)

### `android/local.properties` (Local Build Environment)
```properties
# Absolute path to your local Android SDK installation
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

### In-App DataStore Configuration (`PreferencesManager.kt`)
The Android client persists runtime configurations in Jetpack DataStore:

| Key | Default Value | Configurable In UI | Description |
| :--- | :---: | :---: | :--- |
| `KEY_BASE_URL` | `https://musync-production-2fc5.up.railway.app` | Yes (Settings Screen) | Target backend streaming gateway |
| `KEY_AUDIO_QUALITY` | `low` | Yes (Settings Screen) | Audio bitrate (`saver`, `low`, `standard`, `high`) |
| `KEY_HAPTIC_INTENSITY` | `OFF` | Yes (Settings Screen) | Real-time beat vibration mode (`OFF`, `SUBTLE`, `BALANCED`, `HEAVY`) |
| `KEY_EQUALIZER_PRESET` | `Bass Boost` | Yes (Settings Screen) | Hardware DSP Equalizer preset |
| `KEY_PROVIDER_ID` | `universal` | Yes (Settings Screen) | Active music provider engine |
