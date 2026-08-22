import sys
import json
import yt_dlp


def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/140/251/ba/b/best"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b/best"
    else:  # low / saver
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/bestaudio[ext=m4a]/140/251/ba/b/18/best"

    # tv_embedded and mweb clients bypass YouTube's n-sig throttling (2025+)
    # yt-dlp automatically decodes the 'n' parameter — any URL it returns is already
    # unthrottled. The old ratebypass=yes check was incorrect and blocked valid streams.
    ydl_opts = {
        'format': format_spec,
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'socket_timeout': 20,
        'extractor_args': {
            'youtube': {
                'player_client': ['tv_embedded', 'mweb', 'web']
            }
        }
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            url = f"https://www.youtube.com/watch?v={video_id}"
            info = ydl.extract_info(url, download=False)

            if info:
                stream_url = info.get('url')
                if stream_url:
                    # yt-dlp already handles n-parameter deobfuscation, so any
                    # returned URL is playable — no extra throttle check needed.
                    return {
                        'url': stream_url,
                        'headers': info.get('http_headers', {}),
                        'client': info.get('protocol') or 'tv_embedded',
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': info.get('ext', 'm4a')
                    }
    except Exception as primary_err:
        # Fallback: try android_vr then android clients
        for fallback_client, tag in [(['android_vr', 'ios'], 'android_vr'), (['android', 'web'], 'android')]:
            try:
                fallback_opts = {
                    'format': 'bestaudio/140/251/18/ba/b',
                    'quiet': True,
                    'no_warnings': True,
                    'skip_download': True,
                    'nocheckcertificate': True,
                    'socket_timeout': 20,
                    'extractor_args': {
                        'youtube': {
                            'player_client': fallback_client
                        }
                    }
                }
                with yt_dlp.YoutubeDL(fallback_opts) as ydl:
                    url = f"https://www.youtube.com/watch?v={video_id}"
                    info = ydl.extract_info(url, download=False)
                    resolved_url = info.get('url') if info else None
                    if resolved_url:
                        return {
                            'url': resolved_url,
                            'headers': info.get('http_headers', {}),
                            'client': tag,
                            'quality': quality,
                            'media_type': 'audio',
                            'ext': info.get('ext', 'mp4')
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
