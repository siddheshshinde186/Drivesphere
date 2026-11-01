# DriveSphere Production HTTPS Architecture & Deployment Guide

## 1. Overview
In production, DriveSphere enforces HTTPS for all client traffic.
This guarantees:
1. End-to-end transport encryption for sensitive customer PII, passwords, and payment payloads.
2. Unrestricted browser access to the HTML5 Geolocation API (`navigator.geolocation.watchPosition()`), which modern browsers strictly disable on insecure HTTP contexts.
3. HTTP Strict Transport Security (HSTS) and secure session cookie encapsulation.

---

## 2. Recommended HTTPS Architectures

### Option A: Automated Reverse Proxy via Caddy (Recommended for Standalone VPS)
Caddy automatically manages Let's Encrypt certificates without cron jobs or manual intervention.

```caddyfile
# /etc/caddy/Caddyfile
drivesphere.com {
    reverse_proxy localhost:80 {
        header_up X-Forwarded-Proto https
        header_up X-Real-IP {remote_host}
    }
}
```

### Option B: Cloudflare / AWS Application Load Balancer (ALB)
When using Cloudflare or AWS ALB for SSL termination:
1. Point your DNS to the load balancer / CDN with "Full (Strict)" SSL.
2. The load balancer terminates TLS and forwards traffic to DriveSphere frontend on port 80.
3. DriveSphere's Nginx configuration automatically inspects `X-Forwarded-Proto: http` and returns `301 Moved Permanently` to `https://$host$request_uri`.

### Option C: Nginx Host Reverse Proxy with Certbot
```nginx
server {
    listen 80;
    server_name drivesphere.com www.drivesphere.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name drivesphere.com www.drivesphere.com;

    ssl_certificate /etc/letsencrypt/live/drivesphere.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/drivesphere.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

---

## 3. Security Invariants
- **Zero Secrets in Git**: `certs/`, `*.pem`, `*.key`, and `*.crt` are excluded via `.gitignore`.
- **Permissions Policy**: `Permissions-Policy: geolocation=(self)` is returned on all responses so driver & customer GPS tracking operates seamlessly.
- **CORS & Origin Alignment**: Ensure `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` in production `.env` point to `https://your-domain.com`.

