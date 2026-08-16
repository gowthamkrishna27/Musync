import sys
import json
import yt_dlp

def resolve(video_id, quality="low", media_type="audio"):
    client_configs = [
        ['android_vr', 'web_safari'],
        ['android_vr'],
        ['android'],
        ['web'],
        ['tv_embedded']
    ]

    last_error = ""
    is_video = media_type.lower() == "video" or quality in ['144p', '360p', '480p', '720p', '1080p']

    if is_video:
        if quality in ['144p']:
            format_selector = 'best[height<=144][ext=mp4]/bestvideo[height<=144]+bestaudio/best[height<=144]/best'
        elif quality in ['360p']:
            format_selector = 'itag=18/best[height<=360][ext=mp4]/bestvideo[height<=360]+bestaudio/best[height<=360]/best'
        elif quality in ['480p']:
            format_selector = 'best[height<=480][ext=mp4]/bestvideo[height<=480]+bestaudio/best[height<=480]/best'
        elif quality in ['720p', 'hd']:
            format_selector = 'itag=22/best[height<=720][ext=mp4]/bestvideo[height<=720]+bestaudio/best[height<=720]/best'
        elif quality in ['1080p', 'fhd']:
            format_selector = 'best[height<=1080][ext=mp4]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best'
        else: # auto / high / standard / low
            format_selector = 'itag=22/itag=18/best[height<=720][ext=mp4]/best[ext=mp4]/bestvideo+bestaudio/best'
    else:
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
                    height = info.get('height')
                    fps = info.get('fps')
                    ext = info.get('ext') or ("mp4" if is_video else "webm")
                    output = {
                        'url': stream_url,
                        'headers': info.get('http_headers', {}),
                        'client': clients[0],
                        'quality': quality,
                        'media_type': 'video' if is_video else 'audio',
                        'height': height,
                        'fps': fps,
                        'ext': ext,
                        'available_qualities': ['Auto', '1080p', '720p', '480p', '360p', '144p']
                    }
                    print(json.dumps(output))
                    return
        except Exception as e:
            last_error = str(e)
            continue

    print(json.dumps({'error': f'All clients failed. Last error: {last_error}'}))

if __name__ == '__main__':
    video_id = sys.argv[1] if len(sys.argv) > 1 else ""
    quality = sys.argv[2] if len(sys.argv) > 2 else "low"
    media_type = sys.argv[3] if len(sys.argv) > 3 else ("video" if quality in ['144p', '360p', '480p', '720p', '1080p'] else "audio")
    resolve(video_id, quality, media_type)

