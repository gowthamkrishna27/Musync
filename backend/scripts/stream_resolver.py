import sys
import json
import yt_dlp

def resolve(video_id, quality="low", _media_type="audio"):
    # Quality-aware format selection prioritizing unthrottled direct audio streams
    if quality in ("high", "lossless"):
        format_spec = "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/140/251/ba/b"
    elif quality == "standard":
        format_spec = "bestaudio[abr<=160][ext=m4a]/bestaudio[abr<=160][ext=webm]/140/251/bestaudio/ba/b"
    else:  # low / saver
        format_spec = "bestaudio[abr<=96][ext=m4a]/bestaudio[abr<=96][ext=webm]/bestaudio[ext=m4a]/140/251/ba/b"

    ydl_opts = {
        'format': format_spec,
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'socket_timeout': 12,
        'extractor_args': {
            'youtube': {
                'player_client': ['ios', 'android', 'web']
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
                    headers = info.get('http_headers', {})
                    ext = info.get('ext', 'm4a')
                    client = info.get('protocol') or 'ios'
                    
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': client,
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
    except Exception as e:
        # Fallback with standard android/web client if first attempt encountered format parsing issue
        try:
            fallback_opts = {
                'format': 'bestaudio/140/251/18/ba/b',
                'quiet': True,
                'no_warnings': True,
                'skip_download': True,
                'nocheckcertificate': True,
                'extractor_args': {
                    'youtube': {
                        'player_client': ['android', 'web']
                    }
                }
            }
            with yt_dlp.YoutubeDL(fallback_opts) as ydl:
                url = f"https://www.youtube.com/watch?v={video_id}"
                info = ydl.extract_info(url, download=False)
                if info and info.get('url'):
                    return {
                        'url': info.get('url'),
                        'headers': info.get('http_headers', {}),
                        'client': 'android_fallback',
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
