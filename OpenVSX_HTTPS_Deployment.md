# Deploying Open VSX via HTTPS

There are two methods to map HTTP to HTTPS. The first method is to map HTTP localhost to public HTTPS through ngrok. You can search on Google for more information about this. Here, I will specifically introduce the second method: To ensure that the plugin marketplace can connect and be used smoothly, it must be deployed as an HTTPS site. When deploying privately within or outside the enterprise, self-signed certificates are typically used to bind HTTPS sites.

## Applying for an OpenSSL Self-Signed Certificate (Optional)

If you don't have a self-signed certificate, you can create one using the following steps:

First, use the following command to create a self-signed certificate:

```bash
docker run --rm -it -e CERT_DNS="your_ip" -v $(pwd)/certs:/ssl soulteary/certs-maker
```

The path to the certificate files is as follows:

```bash
ls $(pwd)/certs
```

Copy the certificate files to the Nginx configuration directory:

```bash
sudo mkdir -p /etc/nginx/ssl
sudo cp $(pwd)/certs/your_ip.crt /etc/nginx/ssl/
sudo cp $(pwd)/certs/your_ip.key /etc/nginx/ssl/
```

## Configuring Nginx

Edit the Nginx configuration file:

```bash
sudo nano /etc/nginx/nginx.conf
```

Create and edit the site configuration:

```bash
sudo nano /etc/nginx/sites-available/openvsx
```

The Nginx configuration is as follows:

```nginx
# Handle HTTP requests on port 80
server {
    listen 80;
    server_name your_ip;

    # Redirect all HTTP requests to HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}

# Handle HTTPS requests on port 443
server {
    listen 443 ssl;
    server_name your_ip;

    ssl_certificate /etc/nginx/ssl/your_ip.crt;
    ssl_certificate_key /etc/nginx/ssl/your_ip.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;

    location / {
        proxy_pass http://your_ip:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Create a symbolic link and reload Nginx:

```bash
sudo ln -s /etc/nginx/sites-available/openvsx /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Check and update the configuration:

```bash
sudo grep -r 'your_ip' /etc/nginx/
sudo mv /etc/nginx/sites-available/your_ip.conf /etc/nginx/sites-available/your_ip.conf.disabled
sudo rm /etc/nginx/sites-enabled/your_ip.conf
sudo ln -s /etc/nginx/sites-available/openvsx /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

If you need a complete private deployment solution, please contact me, and I will update it.
