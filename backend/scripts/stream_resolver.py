import sys
import os
import json
import shutil
import tempfile
from pathlib import Path
import yt_dlp


def get_cookie_file():
    """Locate or temporarily materialize YouTube cookies from environment / safe paths."""
    # 1. Direct file path from environment
    env_file = os.environ.get("YOUTUBE_COOKIES_FILE")
    if env_file and os.path.isfile(env_file):
        return env_file

    # 2. Known local files
    candidate_paths = [
        Path(tempfile.gettempdir()) / "musync_yt_cookies.txt",
        Path.cwd() / "musync_yt_cookies.txt",
        Path.cwd() / "cookies.txt",
        Path.cwd() / "scripts" / "cookies.txt"
    ]
    for p in candidate_paths:
        if p.is_file() and p.stat().st_size > 10:
            return str(p)

    # 3. Cookies string in environment variable
    raw_cookies = os.environ.get("YOUTUBE_COOKIES") or os.environ.get("YOUTUBE_COOKIES_BASE64")
    if raw_cookies:
        try:
            content = raw_cookies
            if raw_cookies.startswith("ey") or ("=" in raw_cookies and "\n" not in raw_cookies and len(raw_cookies) > 50):
                import base64
                try:
                    content = base64.b64decode(raw_cookies).decode("utf-8")
                except Exception:
                    content = raw_cookies

            tmp_path = Path(tempfile.gettempdir()) / "musync_yt_cookies.txt"
            tmp_path.write_text(content, encoding="utf-8")
            return str(tmp_path)
        except Exception:
            pass

    return None


def get_js_runtimes():
    """Detect available JavaScript runtime (node or deno or bun) for yt-dlp challenge solver."""
    runtimes = {}
    candidates = [
        shutil.which("node"),
        shutil.which("nodejs"),
        "/usr/local/bin/node",
        "/usr/bin/node",
        "/bin/node"
    ]
    for c in candidates:
        if c and (os.path.exists(c) or shutil.which(c)):
            runtimes["node"] = {"path": c}
            break
    if "node" not in runtimes:
        runtimes["node"] = {}
    return runtimes


def classify_error(err_str: str) -> str:
    """Classify the yt-dlp failure without exposing sensitive data."""
    lower = err_str.lower()
    if "sign in to confirm" in lower or "bot" in lower:
        return "BOT_DETECTION"
    if "login required" in lower or "private video" in lower:
        return "LOGIN_REQUIRED"
    if "po token" in lower or "gvs po token" in lower:
        return "PO_TOKEN_REQUIRED"
    if "requested format is not available" in lower:
        return "FORMAT_UNAVAILABLE"
    if "http error 403" in lower or "forbidden" in lower:
        return "HTTP_403_FORBIDDEN"
    if "http error 429" in lower or "too many requests" in lower:
        return "RATE_LIMITED"
    return "RESOLVER_ERROR"


def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection prioritizing unthrottled playable audio
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/140/251/bestaudio/ba/b/18/best"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b/18/best"
    else:  # low / saver
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/139/249/250/140/251/bestaudio/ba/b/18/best"

    js_runtimes = get_js_runtimes()
    cookie_file = get_cookie_file()

    # Cascade client configurations:
    # 1. android_vr: Highly resilient on datacenter IPs (no PO token challenge)
    # 2. tv_embedded: Reliable player client fallback
    # 3. android / web: Fallbacks if authenticated via cookies
    client_candidates = [
        ['android_vr'],
        ['android_vr', 'tv_embedded'],
        ['android'],
        ['web', 'mweb']
    ]

    last_error = "Unknown resolution error"
    last_error_type = "UNKNOWN"

    for client_list in client_candidates:
        opts = {
            'format': format_spec,
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            'nocheckcertificate': True,
            'geo_bypass': True,
            'socket_timeout': 20,
            'remote_components': ['ejs:github'],
            'extractor_args': {
                'youtube': {
                    'player_client': client_list
                }
            }
        }
        if js_runtimes:
            opts['js_runtimes'] = js_runtimes
        if cookie_file:
            opts['cookiefile'] = cookie_file

        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                url = f"https://www.youtube.com/watch?v={video_id}"
                info = ydl.extract_info(url, download=False)

                if info:
                    # Check direct url or formats list
                    stream_url = info.get('url')
                    if not stream_url and info.get('formats'):
                        # Pick best playable audio format
                        audio_formats = [
                            f for f in info['formats']
                            if (f.get('vcodec') == 'none' or 'audio' in str(f.get('mime_type', ''))) and f.get('url')
                        ]
                        if audio_formats:
                            stream_url = audio_formats[-1].get('url')

                    if stream_url:
                        headers = info.get('http_headers', {})
                        ext = info.get('ext') or ('webm' if 'webm' in stream_url else 'm4a')
                        return {
                            'url': stream_url,
                            'headers': headers,
                            'client': client_list[0],
                            'quality': quality,
                            'media_type': 'audio',
                            'ext': ext,
                            'has_cookies': bool(cookie_file)
                        }
        except Exception as e:
            err_msg = str(e)
            last_error = err_msg
            last_error_type = classify_error(err_msg)

    return {
        'error': f"Failed to resolve audio stream: {last_error}",
        'error_type': last_error_type,
        'has_cookies': bool(cookie_file)
    }


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'Missing video_id parameter', 'error_type': 'INVALID_PARAMS'}))
        sys.exit(1)

    v_id = sys.argv[1]
    qual = sys.argv[2] if len(sys.argv) > 2 else "low"
    m_type = sys.argv[3] if len(sys.argv) > 3 else "audio"

    result = resolve(v_id, qual, m_type)
    print(json.dumps(result))
