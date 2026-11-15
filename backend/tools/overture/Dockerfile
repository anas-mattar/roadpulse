# Small tool image that downloads Overture Maps places for one area (see export.py).
FROM python:3.12-slim
RUN pip install --no-cache-dir duckdb==1.4.1
COPY export.py /export.py
ENTRYPOINT ["python", "/export.py"]
