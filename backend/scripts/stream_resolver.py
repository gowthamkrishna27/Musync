import sys
import json
import shutil
import yt_dlp


def get_js_runtimes():
    """Detect available JavaScript runtime (node or deno or bun) for yt-dlp challenge solver."""
    import os
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


def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection prioritizing unthrottled playable streams
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/140/251/bestaudio/ba/b/18"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b/18"
    else:  # low / saver
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/139/249/250/140/251/bestaudio/ba/b/18"

    js_runtimes = get_js_runtimes()

    # Primary: android client (guaranteed 206 stream download without PO token rejection)
    primary_opts = {
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
                'player_client': ['android']
            }
        }
    }
    if js_runtimes:
        primary_opts['js_runtimes'] = js_runtimes

    try:
        with yt_dlp.YoutubeDL(primary_opts) as ydl:
            url = f"https://www.youtube.com/watch?v={video_id}"
            info = ydl.extract_info(url, download=False)

            if info:
                stream_url = info.get('url')
                if stream_url:
                    headers = info.get('http_headers', {})
                    ext = info.get('ext') or ('webm' if 'webm' in stream_url else 'mp4')
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': 'android',
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
    except Exception as primary_err:
        # Fallback: widen format spec to 18/bestaudio/ba/b with android_vr
        for fallback_client in [['android', 'android_vr'], ['android_vr'], ['web']]:
            try:
                fallback_opts = {
                    'format': '18/bestaudio/ba/b/best',
                    'quiet': True,
                    'no_warnings': True,
                    'skip_download': True,
                    'nocheckcertificate': True,
                    'socket_timeout': 20,
                    'remote_components': ['ejs:github'],
                    'extractor_args': {
                        'youtube': {
                            'player_client': fallback_client
                        }
                    }
                }
                if js_runtimes:
                    fallback_opts['js_runtimes'] = js_runtimes

                with yt_dlp.YoutubeDL(fallback_opts) as ydl:
                    url = f"https://www.youtube.com/watch?v={video_id}"
                    info = ydl.extract_info(url, download=False)
                    resolved_url = info.get('url') if info else None
                    if resolved_url:
                        headers = info.get('http_headers', {})
                        ext = info.get('ext') or ('webm' if 'webm' in resolved_url else 'mp4')
                        return {
                            'url': resolved_url,
                            'headers': headers,
                            'client': fallback_client[0],
                            'quality': quality,
                            'media_type': 'audio',
                            'ext': ext
                        }
            except Exception:
                pass

        return {'error': f"Failed to resolve audio stream: {str(primary_err)}"}

    return {'error': "No audio stream found"}


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'Missing video_id parameter'}))
        sys.exit(1)

    v_id = sys.argv[1]
    qual = sys.argv[2] if len(sys.argv) > 2 else "low"
    m_type = sys.argv[3] if len(sys.argv) > 3 else "audio"

    result = resolve(v_id, qual, m_type)
    print(json.dumps(result))
