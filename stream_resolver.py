import sys
import json
import yt_dlp

def resolve(video_id, quality="low", _media_type="audio"):
    client_configs = [
        ['android_vr', 'android'],
        ['android_vr'],
        ['android'],
    ]

    last_error = ""
    format_selector = 'bestaudio/ba/b'

    for clients in client_configs:
        ydl_opts = {
            'format': format_selector,
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            'nocheckcertificate': True,
            'extractor_args': {
                'youtube': {
                    'player_client': clients
                }
            }
        }

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                url = f"https://www.youtube.com/watch?v={video_id}"
                info = ydl.extract_info(url, download=False)
                
                if not info:
                    continue

                stream_url = info.get('url')
                if stream_url:
                    headers = info.get('http_headers', {})
                    ext = info.get('ext', 'webm')
                    
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': '+'.join(clients),
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
        except Exception as e:
            last_error = str(e)
            continue

    return {'error': f"Failed to resolve audio stream: {last_error}"}

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'Missing video_id parameter'}))
        sys.exit(1)
        
    v_id = sys.argv[1]
    qual = sys.argv[2] if len(sys.argv) > 2 else "low"
    m_type = sys.argv[3] if len(sys.argv) > 3 else "audio"
    
    result = resolve(v_id, qual, m_type)
    print(json.dumps(result))
