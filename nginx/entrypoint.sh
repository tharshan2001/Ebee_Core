#!/bin/sh
set -e

if [ -f /etc/letsencrypt/live/ebee.lk/fullchain.pem ]; then
    echo "SSL certificates found — starting with HTTPS"
    cp /etc/nginx/nginx.conf /etc/nginx/conf.d/default.conf
else
    echo "No SSL certificates found — starting HTTP-only (run certbot to enable HTTPS)"
    cp /etc/nginx/nginx.http.conf /etc/nginx/conf.d/default.conf
fi

exec nginx -g 'daemon off;'
