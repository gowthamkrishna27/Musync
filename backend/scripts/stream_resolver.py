import sys
import json
import yt_dlp

def resolve(video_id, quality="low", _media_type="audio"):
    ydl_opts = {
        'format': 'bestaudio/18/ba/b',
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'nocheckcertificate': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android']
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
                    ext = info.get('ext', 'mp4')
                    
                    return {
                        'url': stream_url,
                        'headers': headers,
                        'client': 'android',
                        'quality': quality,
                        'media_type': 'audio',
                        'ext': ext
                    }
    except Exception as e:
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
