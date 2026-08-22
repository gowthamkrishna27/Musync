import sys
import json
import yt_dlp


def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection prioritizing clean audio streams
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/140/251/bestaudio/ba/b"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b"
    else:  # low / saver (fastest start, least data usage)
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/139/249/250/140/251/bestaudio/ba/b"

    # Multi-client strategy: try android_vr, mweb, web, ios, tv_embedded
    # yt-dlp automatically decodes the 'n' parameter across these clients
    primary_opts = {
        'format': format_spec,
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'socket_timeout': 20,
        'extractor_args': {
            'youtube': {
                'player_client': ['android_vr', 'mweb', 'web', 'ios', 'tv_embedded']
            }
        }
    }

    try:
        with yt_dlp.YoutubeDL(primary_opts) as ydl:
            url = f"https://www.youtube.com/watch?v={video_id}"
            info = ydl.extract_info(url, download=False)

            if info:
                stream_url = info.get('url')
                if stream_url:
                    headers = info.get('http_headers', {})
                    ext = info.get('ext') or ('webm' if 'webm' in stream_url else 'm4a')
                    client = info.get('protocol') or 'android_vr'
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': client,
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
    except Exception as primary_err:
        # Fallback: broaden format spec to any audio/best
        for fallback_client, tag in [(['mweb', 'web'], 'mweb'), (['android', 'ios'], 'android')]:
            try:
                fallback_opts = {
                    'format': 'bestaudio/ba/b/best',
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
                        headers = info.get('http_headers', {})
                        ext = info.get('ext') or ('webm' if 'webm' in resolved_url else 'm4a')
                        return {
                            'url': resolved_url,
                            'headers': headers,
                            'client': tag,
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
