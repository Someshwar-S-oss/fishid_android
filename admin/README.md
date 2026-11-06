# FishID Studio (admin)

A password-protected site where the team edits species details and prices, pre-fills facts from FishBase, and sees a
live phone preview of the species page in English and Tamil. No dependencies (Node 22).

**Save & publish** commits `catalog.json` to a public GitHub repo. The app downloads it from there (`DataSync.kt`):
whenever the phone comes online and when the app returns to the foreground, at most once every 5 minutes; an unchanged
file costs one tiny 304 response. Phones keep their current data until a newer, valid catalog has fully downloaded.

Why GitHub and not Render for the data: Render's free disk is wiped on every restart, and a sleeping free server would
make phones wait ~1 min. GitHub's CDN is always up and keeps every version (roll back by reverting a commit).
Render only hosts this editor; it sleeps after 15 idle minutes and takes ~1 min to wake, which only admins notice.

## Deploy on Render (free), once

1. **Data repo.** On GitHub create a **public**, empty repo named `fishid-data` (under the same account as
   `DataSync.kt`'s `URL_CATALOG`: `Someshwar-S-oss`).
2. **Token.** GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate.
   Repository access: *Only select repositories* → `fishid-data`. Permissions → Repository → *Contents: Read and write*.
   Copy the token.
3. **Push this project** (with `admin/` and `render.yaml`) to GitHub.
4. **Render.** dashboard.render.com → New → Blueprint → connect the `fishid_android` repo and pick the branch.
   It reads `render.yaml` and asks for:
   - `ADMIN_PASSWORD`: the team's sign-in password (long; changing it later signs everyone out)
   - `GITHUB_TOKEN`: the token from step 2
   - `GITHUB_REPO`: `Someshwar-S-oss/fishid-data`
5. Open `https://fishid-admin.onrender.com` (Render shows the exact URL), sign in, change anything, **Save & publish**.
   That creates `catalog.json`. Check it at `https://raw.githubusercontent.com/Someshwar-S-oss/fishid-data/main/catalog.json`.
6. Build and install the app. It picks up each new version within a few minutes of being open and online.

## Rules

- Species can be edited, not added or removed: the model decides which species exist, so that ships with an app update.
  The app also refuses a catalog that drops a species.
- Photos ship with the app and aren't editable here.
- Each save bumps `data_version`. After an app update ships newer bundled data, publish once more so the online copy is newer again.

## Local run / test

```bash
node --env-file=.env.preview server.js   # .env.preview: ADMIN_PASSWORD=..., optional GITHUB_TOKEN / GITHUB_REPO
node test.js                             # parser + validation; add --live to also hit fishbase.se
```
Without `GITHUB_TOKEN`/`GITHUB_REPO` it runs in preview mode: shows the bundled data, saving is off.
