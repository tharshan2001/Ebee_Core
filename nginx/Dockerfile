FROM nginx:alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY nginx.http.conf /etc/nginx/nginx.http.conf
COPY entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
