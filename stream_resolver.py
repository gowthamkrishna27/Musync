import sys
import json
import yt_dlp

def resolve(video_id, quality="high"):
    client_configs = [
        ['android_vr', 'web_safari'],
        ['android_vr'],
        ['android'],
        ['tv_embedded']
    ]

    last_error = ""

    if quality in ['low', 'saver', 'data_saver']:
        format_selector = 'bestaudio[itag=139]/bestaudio[ext=m4a][abr<=64]/worstaudio/ba/b'
    elif quality in ['standard', 'medium']:
        format_selector = 'bestaudio[itag=140]/bestaudio[ext=m4a]/bestaudio[abr<=130]/ba/b'
    else:
        format_selector = 'bestaudio[itag=251]/bestaudio[abr>=160]/bestaudio[itag=140]/ba/b'

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
        url = f"https://www.youtube.com/watch?v={video_id}"
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                stream_url = info.get('url')
                if stream_url:
                    output = {
                        'url': stream_url,
                        'headers': info.get('http_headers', {}),
                        'client': clients[0],
                        'quality': quality
                    }
                    print(json.dumps(output))
                    return
        except Exception as e:
            last_error = str(e)
            continue

    print(json.dumps({'error': f'All clients failed. Last error: {last_error}'}))

if __name__ == '__main__':
    if len(sys.argv) > 2:
        resolve(sys.argv[1], sys.argv[2])
    elif len(sys.argv) > 1:
        resolve(sys.argv[1])
