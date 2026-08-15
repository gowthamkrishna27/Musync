import sys
import json
import yt_dlp

def resolve(video_id):
    client_configs = [
        ['android_vr', 'web_safari'],
        ['android_vr'],
        ['android'],
        ['tv_embedded']
    ]

    last_error = ""

    for clients in client_configs:
        ydl_opts = {
            'format': 'ba/b',
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
        url = f"https://www.youtube.com/watch?v={video_id}"
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                stream_url = info.get('url')
                if stream_url:
                    output = {
                        'url': stream_url,
                        'headers': info.get('http_headers', {}),
                        'client': clients[0]
                    }
                    print(json.dumps(output))
                    return
        except Exception as e:
            last_error = str(e)
            continue

    print(json.dumps({'error': f'All clients failed. Last error: {last_error}'}))

if __name__ == '__main__':
    if len(sys.argv) > 1:
        resolve(sys.argv[1])
