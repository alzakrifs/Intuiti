"use strict";

const fs = require("node:fs");
const path = require("node:path");

// Claude Code stores transcripts under ~/.claude/projects/<encoded>/<id>.jsonl
// where <encoded> is the project path with every non-alphanumeric char
// replaced by "-" (e.g. /home/user/App -> -home-user-App).
function encodeProjectDir(projectPath) {
  return projectPath.replace(/[^a-zA-Z0-9]/g, "-");
}

// Derives a human-readable title from the head of a session transcript:
// a "summary" entry if present, else the first plain-text user message.
// The jsonl format is internal to Claude Code, so parse defensively.
function extractSessionTitle(jsonlHead) {
  let fallback = null;
  for (const line of jsonlHead.split("\n")) {
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    if (entry?.type === "summary" && typeof entry.summary === "string") {
      return entry.summary;
    }
    if (
      !fallback &&
      entry?.type === "user" &&
      typeof entry.message?.content === "string"
    ) {
      const text = entry.message.content.trim();
      // Skip command invocations and injected system content.
      if (text && !text.startsWith("<") && !text.startsWith("Caveat:")) {
        fallback = text;
      }
    }
  }
  return fallback;
}

const UUID_RE = /^[0-9a-fA-F-]{32,40}$/;

function listSessions(projectPath, claudeDir) {
  const dir = path.join(claudeDir, "projects", encodeProjectDir(projectPath));
  let files;
  try {
    files = fs.readdirSync(dir);
  } catch {
    return []; // no sessions recorded for this project yet
  }
  const sessions = [];
  for (const file of files) {
    if (!file.endsWith(".jsonl")) continue;
    const id = file.slice(0, -".jsonl".length);
    if (!UUID_RE.test(id)) continue;
    const full = path.join(dir, file);
    let stat, head;
    try {
      stat = fs.statSync(full);
      const fd = fs.openSync(full, "r");
      const buf = Buffer.alloc(65536);
      const read = fs.readSync(fd, buf, 0, buf.length, 0);
      fs.closeSync(fd);
      head = buf.toString("utf8", 0, read);
    } catch {
      continue;
    }
    const title = extractSessionTitle(head);
    if (!title) continue; // empty/warmup sessions aren't worth resuming
    sessions.push({
      id,
      title: title.length > 100 ? title.slice(0, 100) + "…" : title,
      lastActiveAt: stat.mtime.toISOString(),
    });
  }
  return sessions.sort((a, b) => (a.lastActiveAt < b.lastActiveAt ? 1 : -1));
}

module.exports = { encodeProjectDir, extractSessionTitle, listSessions };
