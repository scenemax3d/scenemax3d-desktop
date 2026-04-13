# itch.io Integration Guide

SceneMax can package your game and upload the generated desktop builds directly to [itch.io](https://itch.io) through itch's official `butler` uploader.

This guide explains:

- what the integration does
- how to install or detect `butler`
- how project settings work
- how authentication works
- how automatic upload behaves during packaging
- how to troubleshoot the most common problems

## What SceneMax Supports

SceneMax can package and optionally upload these desktop targets:

- Windows
- Linux
- macOS

SceneMax uses `butler push` under the hood after packaging finishes.

Current behavior:

- Windows uploads the generated `.exe`
- Linux uploads the generated `.zip`
- macOS uploads the generated `.zip`
- Web Start is packaged locally but is not uploaded to itch.io automatically

## Before You Start

You need:

- an itch.io account
- a game page on itch.io
- the `butler` uploader installed or available to SceneMax
- either:
  - a local `butler login` session on your machine, or
  - a project API key

## Project Settings

Open:

`File > Projects > Project Settings...`

The itch.io integration is configured per project.

Fields:

- `itch.io Game Page`
  - Accepts either a full page URL such as `https://yourname.itch.io/your-game`
  - Or a short target such as `yourname/your-game`
- `Butler Executable`
  - Can point to `butler.exe`
  - Can also be populated automatically by browsing to the downloaded `butler` zip
  - Can be auto-filled with `Detect`
- `Windows Channel`
  - Default fallback is `windows`
- `Linux Channel`
  - Default fallback is `linux`
- `macOS Channel`
  - Default fallback is `macos`
- `Butler API Key`
  - Optional
  - If left blank, SceneMax can use a local `butler login` session instead

## Installing Butler

### Option 1: Browse To The Downloaded Zip

This is the easiest path inside SceneMax.

1. Download `butler` from [https://itchio.itch.io/butler](https://itchio.itch.io/butler)
2. Open `File > Projects > Project Settings...`
3. In `Butler Executable`, click `Browse...`
4. Select the downloaded `butler` `.zip`
5. SceneMax extracts it into the SceneMax installation folder under:

`tools/butler`

6. SceneMax finds `butler.exe`, fills the field automatically, and saves that path for the current project

You do not need to unpack the zip manually when using this flow.

### Option 2: Detect An Existing Installation

Click `Detect`.

SceneMax checks these locations:

- the SceneMax local tools folder under `tools/butler`
- the itch desktop app's bundled `butler` installation

When detection succeeds, SceneMax shows:

- the resolved `butler.exe` path
- where it was found
- whether a previous local `butler login` session exists on the machine

### Option 3: Browse To `butler.exe`

If you already extracted `butler` yourself, you can browse directly to `butler.exe`.

## Authentication

SceneMax supports two authentication paths.

### Option 1: Local `butler login`

This is the most user-friendly option for normal desktop use.

You can trigger it in two ways:

- click `Butler Login...` in Project Settings
- or start packaging with automatic itch.io upload enabled and let SceneMax offer to run login for you when no API key is saved

What happens:

1. SceneMax launches `butler login`
2. A login dialog appears in SceneMax and shows butler output
3. itch.io authentication opens in your browser
4. After you approve access, butler stores a local login session on your machine
5. SceneMax can use that session for later uploads

SceneMax detects local butler credentials from the standard per-user butler login location.

### Option 2: Project API Key

If you prefer not to use `butler login`, you can paste a project API key into the `Butler API Key` field.

Notes:

- the API key is stored locally through SceneMax's app database
- it is scoped to the current project in SceneMax
- it is not written into `projects/projects.json`

When an API key is present, SceneMax uses it for upload by setting `BUTLER_API_KEY` for the butler process.

## Packaging And Upload Flow

Open the package dialog from the regular packaging action and select the target platforms you want.

Then enable:

`Automatically upload desktop builds to itch.io with butler`

Before packaging starts, SceneMax checks:

- that a project is active
- that the project has a valid itch.io game page / target
- that at least one desktop target is selected
- that `butler` can be found
- that authentication is available

If no API key is saved and no local `butler login` session exists, SceneMax asks:

- whether you want it to run `butler login` for you immediately

If you confirm, SceneMax guides you through the login flow and then continues.

## How Upload Targets Are Built

SceneMax normalizes the game page field into an itch target in the form:

`username/game-name`

For each selected platform it uploads to:

- `username/game-name:windows`
- `username/game-name:linux`
- `username/game-name:macos`

unless you override the channel names in Project Settings.

Examples:

- page: `https://adibarda.itch.io/racing-demo`
- resulting target: `adibarda/racing-demo`

With default channels, SceneMax uploads to:

- `adibarda/racing-demo:windows`
- `adibarda/racing-demo:linux`
- `adibarda/racing-demo:macos`

## What The User Sees

During upload, the packaging dialog shows live status updates from the upload step.

After packaging finishes, SceneMax includes upload notes in the completion message, including:

- which builds were uploaded
- the itch target and channel used

The Detect button also gives immediate feedback when it finds `butler`, including previous login status.

## Recommended Setup

For most users, the simplest setup is:

1. Create your itch.io game page
2. Open `Project Settings`
3. Paste the game page URL
4. Browse to the downloaded `butler` zip
5. Click `Butler Login...`
6. Save the settings
7. Package your Windows, Linux, or macOS build
8. Enable automatic itch.io upload

This avoids manual command-line setup in most cases.

## Troubleshooting

### Detect Does Not Find Butler

Try these checks:

- make sure you previously installed `butler` into SceneMax through the zip browse flow
- or make sure the itch desktop app is installed on this machine
- if needed, use `Browse...` and select either the downloaded zip or `butler.exe`

### SceneMax Says Butler Is Not Found

Possible causes:

- the configured path points to a missing file
- `butler` was never installed
- a command-line-only `butler` installation is not on `PATH`

Fix:

- re-run `Browse...` and select the downloaded zip
- or click `Detect`
- or browse directly to `butler.exe`

### Upload Asks For Login Even Though Butler Exists

`butler` being installed is separate from being authenticated.

If no API key is saved and no local login session exists:

- click `Butler Login...`
- or accept the login prompt during packaging

### Upload Fails After Login

Check:

- the itch.io game page field is correct
- the game page belongs to the account you logged into with butler
- the channel names are valid for your release workflow
- the selected artifact actually exists for the chosen platform

### API Key Or Login Session?

Use `butler login` if:

- you want the easiest desktop workflow
- you are publishing interactively from your own machine

Use an API key if:

- you prefer explicit project credentials
- you do not want to rely on the machine's local butler login session

## Security Notes

- Project page, butler path, and channel names are stored with project settings
- The API key is stored locally in SceneMax's app database for the project
- A local `butler login` session is stored by butler on the current machine, outside the project files

## Current Limitations

- automatic itch.io upload currently supports desktop targets only
- Web Start packaging is not pushed to itch.io automatically
- SceneMax does not create or edit the itch.io page itself; the game page must already exist
- SceneMax does not choose pricing, metadata, screenshots, or page design on itch.io

## Summary

The intended SceneMax workflow is:

1. configure the itch.io page in Project Settings
2. install or detect `butler`
3. authenticate with either `Butler Login...` or an API key
4. package the game
5. enable automatic itch.io upload

Once that is set up, SceneMax can package and publish desktop builds to itch.io in one pass.
