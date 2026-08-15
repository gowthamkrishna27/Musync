"""
Musync ytmusicapi Bridge Server
--------------------------------
A production-ready REST API backend powered by ytmusicapi (https://github.com/sigma67/ytmusicapi.git).
Deployable to Heroku, Render, Railway, Fly.io, or any VPS.
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
from ytmusicapi import YTMusic
import os
import sys

app = Flask(__name__)
CORS(app)

# Initialize YTMusic
try:
    yt = YTMusic()
    print("✓ Initialized YTMusic successfully.")
except Exception as e:
    print(f"Warning initializing YTMusic: {e}")
    yt = None

@app.route("/")
def home():
    return jsonify({
        "status": "online",
        "service": "Musync ytmusicapi Cloud Server",
        "version": "1.0.0",
        "endpoints": {
            "search": "/search?query=<song_or_artist>",
            "song": "/song?id=<video_id>",
            "trending": "/trending",
            "charts": "/charts"
        }
    })

@app.route("/health")
def health():
    return jsonify({"status": "healthy", "ytmusic": yt is not None})

@app.route("/search", methods=["GET"])
@app.route("/result/", methods=["GET"])
def search():
    query = request.args.get("query") or request.args.get("q") or "Trending"
    try:
        results = yt.search(query, filter="songs") if yt else []
        if not results and yt:
            results = yt.search(query)

        songs = []
        for item in results:
            video_id = item.get("videoId")
            if not video_id:
                continue

            title = item.get("title", "Unknown Title")
            artists = item.get("artists", [])
            artist_name = ", ".join([a.get("name", "") for a in artists]) if artists else item.get("author", "YouTube Artist")
            album_info = item.get("album")
            album_name = album_info.get("name", title) if isinstance(album_info, dict) else title

            thumbnails = item.get("thumbnails", [])
            thumb_url = thumbnails[-1].get("url") if thumbnails else f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"

            duration_sec = item.get("duration_seconds")
            if not duration_sec and "duration" in item:
                parts = str(item.get("duration")).split(":")
                try:
                    duration_sec = int(parts[0]) * 60 + int(parts[1]) if len(parts) == 2 else 180
                except:
                    duration_sec = 180

            # High-compatibility Direct M4A audio stream
            stream_url = f"https://inv.nadeko.net/latest_version?id={video_id}&itag=140"

            songs.append({
                "videoId": video_id,
                "id": video_id,
                "songid": video_id,
                "title": title,
                "song": title,
                "singers": artist_name,
                "artist": artist_name,
                "album": album_name,
                "image_url": thumb_url,
                "image": thumb_url,
                "duration": str(duration_sec or 180),
                "duration_seconds": duration_sec or 180,
                "url": stream_url,
                "media_url": stream_url
            })

        return jsonify(songs)
    except Exception as e:
        return jsonify({"error": str(e), "data": []}), 500

@app.route("/song", methods=["GET"])
@app.route("/song/", methods=["GET"])
def get_song():
    video_id = request.args.get("id") or request.args.get("query")
    if not video_id:
        return jsonify({"error": "Missing video ID"}), 400

    try:
        song = yt.get_song(video_id) if yt else {}
        stream_url = f"https://inv.nadeko.net/latest_version?id={video_id}&itag=140"
        return jsonify({
            "videoId": video_id,
            "id": video_id,
            "url": stream_url,
            "media_url": stream_url,
            "details": song
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/trending", methods=["GET"])
@app.route("/charts", methods=["GET"])
def get_trending():
    try:
        charts = yt.get_charts(country="ZZ") if yt else {}
        videos = charts.get("videos", {}).get("items", [])
        return jsonify(videos)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    print(f"Starting Musync ytmusicapi Server on port {port}...")
    app.run(host="0.0.0.0", port=port, debug=False)
