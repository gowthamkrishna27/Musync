import sys
import json
import yt_dlp

def _is_throttled(url):
    """Detect YouTube n-sig throttled URLs (ratebypass absent = throttled)."""
    return url and 'ratebypass=yes' not in url and 'ratebypass%3Dyes' not in url


def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection prioritizing unthrottled direct audio streams
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/140/251/ba/b/best"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b/best"
    else:  # low / saver
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/bestaudio[ext=m4a]/140/251/ba/b/18/best"

    # tv_embedded and mweb clients bypass YouTube's n-sig throttling (2025+)
    # ios/android clients are frequently rate-limited on shared server IPs
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
                if stream_url and not _is_throttled(stream_url):
                    headers = info.get('http_headers', {})
                    ext = info.get('ext', 'm4a')
                    client = info.get('protocol') or 'tv_embedded'
                    
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': client,
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
                elif stream_url and _is_throttled(stream_url):
                    # Throttled URL detected — fall through to fallback clients
                    raise Exception(f"Throttled stream detected for {video_id}, trying fallback clients")
    except Exception as e:
        # Fallback: try android_vr + ios clients (different token path, often unthrottled)
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
                    if resolved_url and not _is_throttled(resolved_url):
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

        return {'error': f"Failed to resolve audio stream: {str(e)}"}

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
