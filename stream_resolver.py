import sys
import json
import yt_dlp

def resolve(video_id):
    ydl_opts = {
        'format': 'ba/b',
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android_vr', 'android']
            }
        }
    }
    url = f"https://www.youtube.com/watch?v={video_id}"
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            output = {
                'url': info.get('url'),
                'headers': info.get('http_headers', {})
            }
            print(json.dumps(output))
    except Exception as e:
        print(json.dumps({'error': str(e)}))

if __name__ == '__main__':
    if len(sys.argv) > 1:
        resolve(sys.argv[1])
