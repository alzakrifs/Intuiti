# Intuiti

Start a named Claude Code session on your PC from your phone.

Intuiti is a tiny web server you run on your PC. From your phone's browser (on the same Wi-Fi), you type a session name, pick a project folder, and tap **Start** — the server launches `claude --remote-control "<name>"` on the PC and shows you a tappable **Open in Claude** link. From there you control the session in the Claude mobile app.

## Prerequisites

- **Node.js 18+** on the PC.
- **Claude Code CLI** installed and logged in with a claude.ai account (Pro, Max, Team, or Enterprise — remote control does not work with API keys). Verify by running `claude` manually in a terminal once; also accept the workspace-trust dialog for each project folder you plan to use.
- Phone and PC on the **same Wi-Fi network**.

## Setup

```
npm install
```

Then edit `config.json`:

```json
{
  "port": 3777,
  "projects": [
    "C:\\Users\\me\\projects\\my-app",
    "C:\\Users\\me\\projects\\another-app"
  ],
  "claudeCmd": null
}
```

- `projects` — the folders shown in the picker on your phone. **In JSON, Windows backslashes must be doubled** (`C:\\Users\\...`) — this is the most common setup mistake.
- `port` — change if 3777 is taken.
- `claudeCmd` — leave `null` normally. If `claude` is not on your PATH, set it to the full path of the CLI (string or array form, e.g. `["C:\\Users\\me\\AppData\\Roaming\\npm\\claude.cmd"]`).

## Run

```
npm start
```

The console prints the address to open from your phone, e.g. `http://192.168.1.23:3777`.

> **Windows Firewall:** the first run may show a firewall prompt — click **Allow** for **Private networks**. If you dismissed it and the phone can't connect, open Windows Security → Firewall & network protection → Allow an app through firewall, and enable Node.js for private networks.

## Use

1. Open the printed address in your phone's browser.
2. Enter a session name, pick a project folder, tap **Start session**.
3. When the badge turns green (**running**), tap **Open in Claude** — the session opens on claude.ai/code and hands off to the Claude app. It also appears in the Claude app's **Code** tab.

Sessions keep running on the PC until you exit them (from the app or the PC). Keep the Intuiti server window open; closing it does not kill already-started Claude sessions, but you lose the list of links (the sessions remain reachable from the Claude app's Code tab).

## Troubleshooting

- **Session stuck on "starting" then errors with a timeout** — the CLI is probably waiting for login. Run `claude` manually on the PC and complete `/login`.
- **Error mentions "'claude' is not recognized"** — the CLI isn't on PATH for the server process. Set `claudeCmd` in `config.json` to the CLI's full path.
- **"Port 3777 is already in use"** — change `port` in `config.json`.
- **"Project folder does not exist"** — the path in `config.json` is wrong or stale (check the doubled backslashes).
- The session list is in memory only; restarting the server clears it.

## Security note

There is no authentication: anyone on your Wi-Fi network can open the page and start Claude Code sessions in the configured folders. Only run it on networks you trust.

## Development / testing without the real CLI

`test/fake-claude.js` simulates the CLI (prints a fake session URL after ~1 s). Point `claudeCmd` at it to test the whole flow on any OS:

```json
"claudeCmd": ["node", "test/fake-claude.js"]
```

Add `"--fail"` to the array to simulate a login failure. Unit tests: `npm test`.
