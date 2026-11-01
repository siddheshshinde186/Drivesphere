FROM nginx:alpine

# Copy static frontend assets
COPY . /usr/share/nginx/html/

# Copy custom nginx reverse proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
