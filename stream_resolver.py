import sys
import json
import yt_dlp

def resolve(video_id):
    # Datacenter-resilient player client priorities
    client_configs = [
        ['tv_embedded'],
        ['android_vr'],
        ['android_vr', 'android'],
        ['android']
    ]

    for clients in client_configs:
        ydl_opts = {
            'format': 'ba/b',
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            'extractor_args': {
                'youtube': {
                    'player_client': clients,
                    'player_skip': ['webpage', 'configs']
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
        except Exception:
            continue

    print(json.dumps({'error': 'Stream extraction failed for video: ' + str(video_id)}))

if __name__ == '__main__':
    if len(sys.argv) > 1:
        resolve(sys.argv[1])
