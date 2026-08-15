import sys
import json
import urllib.request
import yt_dlp

def resolve(video_id):
    # 1. Primary: yt-dlp with datacenter-bypass client rotation
    client_chains = [
        ['android_vr', 'ios', 'web_safari'],
        ['tv_embedded', 'android', 'mweb'],
        ['android']
    ]

    for clients in client_chains:
        ydl_opts = {
            'format': 'ba/b',
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
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
                        'headers': info.get('http_headers', {})
                    }
                    print(json.dumps(output))
                    return
        except Exception:
            continue

    # 2. Fallback: Query Piped public instances
    piped_instances = [
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacydev.net",
        "https://pipedapi.leptons.xyz"
    ]
    for base in piped_instances:
        try:
            req = urllib.request.Request(f"{base}/streams/{video_id}", headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=4) as resp:
                data = json.loads(resp.read().decode())
                audio_streams = data.get('audioStreams', [])
                if audio_streams:
                    audio_streams.sort(key=lambda s: s.get('bitrate', 0), reverse=True)
                    print(json.dumps({
                        'url': audio_streams[0]['url'],
                        'headers': {"User-Agent": "Mozilla/5.0"}
                    }))
                    return
        except Exception:
            continue

    print(json.dumps({'error': 'All stream extraction methods exhausted'}))

if __name__ == '__main__':
    if len(sys.argv) > 1:
        resolve(sys.argv[1])
