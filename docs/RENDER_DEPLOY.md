# Deploy To Render

As of April 24, 2026, Render supports free Web Services and free Static Sites, but free web services spin down after 15 minutes of inactivity and their local filesystem is ephemeral. That means the current SQLite database will be lost on redeploy, restart, or spin-down. Source: [Render free services](https://render.com/docs/free).

This repo is prepared so you can deploy:

- `backend/` as a Render **Web Service**
- `frontend/` as a Render **Static Site**

The repo includes:

- `.node-version` pinned to `22.22.0`
- `render.yaml` blueprint for both services

## Important Limitation

The current backend uses SQLite. On Render free web services, SQLite data is temporary because Render's free local filesystem is not persistent. For a real persistent hosted demo, the next upgrade should be migrating the backend from SQLite to Render Postgres.

## Fastest Deploy Steps

1. Push your latest code to GitHub.
2. Sign in to [Render](https://render.com/).
3. In Render, choose **New +** -> **Blueprint**.
4. Connect your GitHub repository:
   - `maherBekdache/Mobile-Network-Analyzer`
5. Render will detect `render.yaml` and create:
   - `maher-mobile-network-analyzer-api`
   - `maher-mobile-network-analyzer-web`
6. Approve the blueprint deployment.
7. Wait for both services to finish building.

## URLs

If the service names are available, the URLs should be:

- Backend:
  - `https://maher-mobile-network-analyzer-api.onrender.com`
- Frontend:
  - `https://maher-mobile-network-analyzer-web.onrender.com`

If Render changes the backend URL because the name is already taken, update the frontend Static Site environment variable:

- `VITE_API_BASE=<your-backend-url>`

Then trigger a redeploy of the static site.

## Manual Deploy Alternative

If you do not want to use Blueprint:

### Backend

1. Render -> **New +** -> **Web Service**
2. Connect the repo
3. Use:
   - Runtime: `Node`
   - Root directory: leave blank
   - Build command: `cd backend && npm install`
   - Start command: `cd backend && npm start`
4. Add environment variables:
   - `NODE_VERSION=22.22.0`
   - `CORS_ORIGIN=*`
   - `DB_FILE=./data/network-analyzer.sqlite`

### Frontend

1. Render -> **New +** -> **Static Site**
2. Connect the repo
3. Use:
   - Build command: `cd frontend && npm install && npm run build`
   - Publish directory: `frontend/dist`
4. Add environment variable:
   - `VITE_API_BASE=https://<your-backend-service>.onrender.com`

## What Already Works For Render

- Backend already binds to `0.0.0.0` and uses `process.env.PORT`.
- Frontend already reads `VITE_API_BASE`.
- Repo now includes deployment config.

## Recommended Next Step If You Want Real Persistence

Migrate backend storage from SQLite to Postgres, then point the backend to a Render Postgres instance. Render explicitly documents that free web services lose local filesystem changes after redeploy/restart/spin-down: [Render free web services](https://render.com/docs/free).
