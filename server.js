"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const express = require("express");
const pty = require("@lydell/node-pty");
const { stripAnsi, extractSessionUrl } = require("./lib/parse-url.js");

const CONFIG_PATH = path.join(__dirname, "config.json");
const URL_TIMEOUT_MS = 45_000;
const OUTPUT_WINDOW_BYTES = 16_384;
// Letters, digits, spaces and a few safe punctuation chars; blocks cmd.exe
// metacharacters since on Windows the command line goes through cmd /c.
const NAME_RE = /^[\w\s.,()'-]{1,60}$/;

function loadConfig() {
  let raw;
  try {
    raw = fs.readFileSync(CONFIG_PATH, "utf8");
  } catch {
    console.error(`Missing ${CONFIG_PATH}. Create it with { "port": 3777, "projects": ["C:\\\\path\\\\to\\\\project"] }.`);
    process.exit(1);
  }
  let config;
  try {
    config = JSON.parse(raw);
  } catch (err) {
    console.error(`config.json is not valid JSON: ${err.message}`);
    console.error("Tip: Windows paths must use escaped backslashes, e.g. \"C:\\\\Users\\\\me\\\\projects\\\\app\"");
    process.exit(1);
  }
  if (!Array.isArray(config.projects)) {
    console.error('config.json must have a "projects" array of folder paths.');
    process.exit(1);
  }
  return {
    port: Number.isInteger(config.port) ? config.port : 3777,
    projects: config.projects.map(String),
    claudeCmd: config.claudeCmd ?? null,
  };
}

const config = loadConfig();

function resolveClaudeCommand(sessionName) {
  if (config.claudeCmd) {
    const parts = Array.isArray(config.claudeCmd)
      ? config.claudeCmd.slice()
      : String(config.claudeCmd).split(/\s+/);
    return { file: parts[0], args: [...parts.slice(1), "--remote-control", sessionName] };
  }
  if (process.platform === "win32") {
    // The claude CLI is usually a .cmd shim, which ConPTY's CreateProcess
    // can't execute directly; cmd.exe resolves it from PATH.
    return { file: "cmd.exe", args: ["/c", "claude", "--remote-control", sessionName] };
  }
  return { file: "claude", args: ["--remote-control", sessionName] };
}

// id -> { id, name, projectPath, status, url, error, startedAt, pty, buf }
const sessions = new Map();

function publicSession(s) {
  const { id, name, projectPath, status, url, error, startedAt } = s;
  return { id, name, projectPath, status, url, error, startedAt };
}

function startSession(name, projectPath) {
  const { file, args } = resolveClaudeCommand(name);
  const proc = pty.spawn(file, args, {
    name: "xterm-color",
    cols: 200, // wide enough that the TUI never wraps the session URL
    rows: 50,
    cwd: projectPath,
    env: process.env,
  });

  const session = {
    id: crypto.randomUUID(),
    name,
    projectPath,
    status: "starting",
    url: null,
    error: null,
    startedAt: new Date().toISOString(),
    pty: proc,
    buf: "",
  };
  sessions.set(session.id, session);

  const timer = setTimeout(() => {
    if (session.status !== "starting") return;
    session.status = "error";
    session.error =
      "Timed out waiting for the session URL. Is the Claude CLI logged in? " +
      "Try running 'claude' manually on the PC.";
    console.error(`[${session.name}] timed out; last output:\n${outputTail(session)}`);
    // Don't kill the process: it may be sitting on a login prompt that the
    // user can complete at the PC.
  }, URL_TIMEOUT_MS);

  // Output must always be consumed or ConPTY can stall the child, so this
  // handler stays attached for the life of the process.
  proc.onData((chunk) => {
    session.buf = (session.buf + chunk).slice(-OUTPUT_WINDOW_BYTES);
    if (session.status !== "starting") return;
    const url = extractSessionUrl(session.buf);
    if (url) {
      session.status = "running";
      session.url = url;
      clearTimeout(timer);
      console.log(`[${session.name}] running: ${url}`);
    }
  });

  proc.onExit(({ exitCode }) => {
    clearTimeout(timer);
    if (session.status === "starting") {
      session.status = "error";
      const tail = outputTail(session);
      session.error =
        `claude exited (code ${exitCode}) before printing a session URL.` +
        (tail ? `\nOutput: ${tail}` : "");
      console.error(`[${session.name}] ${session.error}`);
    } else if (session.status === "running") {
      session.status = "exited";
    }
  });

  return session;
}

function outputTail(session, bytes = 2048) {
  return stripAnsi(session.buf).trim().slice(-bytes);
}

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

app.get("/api/projects", (req, res) => {
  res.json({
    // Not path.basename: config may hold Windows paths while running on
    // another OS (e.g. testing the setup on Linux/macOS).
    projects: config.projects.map((p) => ({
      name: p.split(/[\\/]/).filter(Boolean).pop() || p,
      path: p,
    })),
  });
});

app.get("/api/sessions", (req, res) => {
  const list = [...sessions.values()]
    .sort((a, b) => (a.startedAt < b.startedAt ? 1 : -1))
    .map(publicSession);
  res.json({ sessions: list });
});

app.post("/api/sessions", (req, res) => {
  const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
  const projectPath = req.body?.projectPath;

  if (!NAME_RE.test(name)) {
    return res.status(400).json({
      error: "Session name must be 1-60 characters: letters, digits, spaces, . , ( ) ' -",
    });
  }
  if (!config.projects.includes(projectPath)) {
    return res.status(400).json({ error: "Unknown project folder." });
  }
  if (!fs.existsSync(projectPath)) {
    return res.status(400).json({
      error: "Project folder does not exist on disk. Update config.json.",
    });
  }

  let session;
  try {
    session = startSession(name, projectPath);
  } catch (err) {
    return res.status(500).json({ error: `Failed to start claude: ${err.message}` });
  }
  res.status(201).json(publicSession(session));
});

const server = app.listen(config.port, "0.0.0.0", () => {
  const addresses = Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === "IPv4" && !i.internal)
    .map((i) => i.address);
  console.log("Intuiti is running. Open from your phone (same Wi-Fi):");
  for (const addr of addresses) console.log(`  http://${addr}:${config.port}`);
  if (addresses.length === 0) console.log(`  http://localhost:${config.port} (no LAN address found)`);
});

server.on("error", (err) => {
  if (err.code === "EADDRINUSE") {
    console.error(
      `Port ${config.port} is already in use. Change "port" in config.json or close the other program.`
    );
    process.exit(1);
  }
  throw err;
});
