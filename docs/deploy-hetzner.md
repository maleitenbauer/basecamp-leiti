# Deploying Basecamp to the Hetzner server

Flow: push/merge to `main` → GitHub-hosted job runs tests → **self-hosted runner on the server** runs
`docker compose up -d --build` → the app is served over Tailscale HTTPS only (nothing public).

Assumes Ubuntu 22.04/24.04 and Tailscale already installed and logged in on the server.

## 1. Deploy user and Docker

```bash
# as root / sudo user
adduser --disabled-password --gecos "" deploy
curl -fsSL https://get.docker.com | sh          # Docker Engine + compose plugin
usermod -aG docker deploy
mkdir -p /opt/basecamp && chown deploy:deploy /opt/basecamp
```

## 2. Production env file (secrets stay on the server, never in git)

```bash
sudo -u deploy bash -c 'cat > /opt/basecamp/.env <<EOF
POSTGRES_DB=basecamp
POSTGRES_USER=basecamp
POSTGRES_PASSWORD=$(openssl rand -hex 24)
BASECAMP_ADMIN_USER=admin
BASECAMP_ADMIN_PASSWORD=<choose a long password, 12-72 characters>
EOF
chmod 600 /opt/basecamp/.env'
```

The workflow reads it via `--env-file /opt/basecamp/.env`. Postgres only initialises its user/password on the
first start of an empty volume, so choose the password before the first deploy.

## 3. Self-hosted GitHub Actions runner

1. GitHub repo → **Settings → Actions → Runners → New self-hosted runner** → Linux x64.
2. On the server, as `deploy`, run the shown download/`config.sh` commands in `/opt/basecamp/actions-runner`.
   When asked for labels, add **`basecamp`** (the workflow uses `runs-on: [self-hosted, basecamp]`).
3. Install as a service (as root): 
   ```bash
   cd /opt/basecamp/actions-runner
   sudo ./svc.sh install deploy && sudo ./svc.sh start && sudo ./svc.sh status
   ```
   The runner only makes an outbound connection to GitHub, so no inbound port is opened.

## 4. Tailscale HTTPS

1. Tailscale admin console → **DNS** → enable MagicDNS and **HTTPS Certificates**.
2. On the server:
   ```bash
   sudo tailscale serve --bg 3000      # https://<server>.<tailnet>.ts.net -> localhost:3000 (web container)
   tailscale serve status
   ```
3. Open `https://<server>.<tailnet>.ts.net` from a device on your tailnet. The **Backend** card should be green.

## 5. First deploy

The workflow deploys on pushes to `main`; PRs only run tests. Push the `initial-setup` branch, open a PR, wait for
the `test` job, merge → the `deploy` job runs on your server. Watch it under the repo's **Actions** tab.
If it fails, the job prints the last 100 API log lines; on the server: `docker compose -p basecamp logs -f api`.

## 6. Backups

```bash
sudo -u deploy crontab -e
# add:
15 3 * * * cd /opt/basecamp && POSTGRES_USER=basecamp POSTGRES_DB=basecamp /path/to/checkout/scripts/backup.sh >> /opt/basecamp/backup.log 2>&1
```

The runner's checkout lives in `/opt/basecamp/actions-runner/_work/basecamp-leiti/basecamp-leiti/`; use
`scripts/backup.sh` from there, or copy it to `/opt/basecamp/backup.sh`. Dumps go to `/opt/basecamp/backups`
(14 days kept). Later: sync that folder to a Hetzner Storage Box. Restore:
`gunzip -c file.sql.gz | docker compose -p basecamp exec -T postgres psql -U basecamp basecamp`.

## Notes

- Ports 5432/8080/3000 are bound to `127.0.0.1` on the server; only Tailscale Serve exposes the app.
- The tailnet is the outer layer; the app has its own login on top (see "Login and users" below).
- A self-hosted runner executes whatever is in the workflow files, so keep the repo private and protect `main`.

## Login and users

- The first admin is created from `BASECAMP_ADMIN_USER` / `BASECAMP_ADMIN_PASSWORD` **only while the user table is empty**.
  If the variable is missing on the very first start, the API refuses to start and the deploy job fails with a
  clear message. After the first login, change the password under **Account** and delete the password line from
  `/opt/basecamp/.env`.
- Further users: **Users** page (admins only). Disabling a user, changing their role or resetting their password
  signs them out everywhere.
- A lost phone: disable the user (signs out all devices), or delete rows from `spring_session` for that
  `principal_name`.
- Sessions live in Postgres: 90 days idle timeout (sliding), and the cookie itself expires 180 days after login.
