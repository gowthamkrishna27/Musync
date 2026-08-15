FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ytmusic_server.py .
COPY Procfile .

EXPOSE 5000

CMD ["gunicorn", "ytmusic_server:app", "--bind", "0.0.0.0:5000", "--workers", "2", "--timeout", "120"]
