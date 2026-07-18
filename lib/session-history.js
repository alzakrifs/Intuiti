"use strict";

const fs = require("node:fs");
const path = require("node:path");

const HEAD_BYTES = 65536;
const TAIL_BYTES = 32768;

// Claude Code stores transcripts under ~/.claude/projects/<encoded>/<id>.jsonl
// where <encoded> is the project path with every non-alphanumeric char
// replaced by "-" (e.g. /home/user/App -> -home-user-App).
function encodeProjectDir(projectPath) {
  return projectPath.replace(/[^a-zA-Z0-9]/g, "-");
}

function readChunk(fullPath, position, length) {
  const fd = fs.openSync(fullPath, "r");
  try {
    const buf = Buffer.alloc(length);
    const read = fs.readSync(fd, buf, 0, length, position);
    return buf.toString("utf8", 0, read);
  } finally {
    fs.closeSync(fd);
  }
}

// Claude Code titles sessions with generated "summary" entries. A summary's
// leafUuid points at the last message (at summarization time) of the
// conversation it titles — and the entry may live in a DIFFERENT session file
// than the conversation it describes (e.g. when a session continues an older
// chain). So titling takes two passes: collect summaries from every file,
// then match each session's recent message uuids against them.
function collectSummaries(head, map) {
  let first = null;
  for (const line of head.split("\n")) {
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    if (entry?.type === "summary" && typeof entry.summary === "string") {
      first = first ?? entry.summary;
      if (typeof entry.leafUuid === "string" && map) {
        map.set(entry.leafUuid, entry.summary);
      }
    }
  }
  return first; // the file's own summary, if any
}

const UUID_IN_LINE_RE = /"uuid":"([0-9a-fA-F-]{36})"/g;

// Message uuids near the end of the transcript, most recent first.
function tailUuids(tail) {
  const uuids = [];
  let m;
  while ((m = UUID_IN_LINE_RE.exec(tail)) !== null) uuids.push(m[1]);
  return uuids.reverse();
}

// Fallback title: the first plain-text user message.
// The jsonl format is internal to Claude Code, so parse defensively.
function firstUserMessage(head) {
  for (const line of head.split("\n")) {
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    if (entry?.type === "user" && typeof entry.message?.content === "string") {
      const text = entry.message.content.trim();
      // Skip command invocations and injected system content.
      if (text && !text.startsWith("<") && !text.startsWith("Caveat:")) {
        return text;
      }
    }
  }
  return null;
}

// Title from a single transcript's head, without cross-file context.
function extractSessionTitle(jsonlHead) {
  return collectSummaries(jsonlHead, null) || firstUserMessage(jsonlHead);
}

const UUID_FILE_RE = /^[0-9a-fA-F-]{32,40}$/;

function listSessions(projectPath, claudeDir) {
  const dir = path.join(claudeDir, "projects", encodeProjectDir(projectPath));
  let files;
  try {
    files = fs.readdirSync(dir);
  } catch {
    return []; // no sessions recorded for this project yet
  }

  // Pass 1: read each candidate's head/tail once; collect all summaries.
  const summariesByLeaf = new Map();
  const candidates = [];
  for (const file of files) {
    if (!file.endsWith(".jsonl")) continue;
    const id = file.slice(0, -".jsonl".length);
    if (!UUID_FILE_RE.test(id)) continue;
    const full = path.join(dir, file);
    let stat, head, tail;
    try {
      stat = fs.statSync(full);
      head = readChunk(full, 0, HEAD_BYTES);
      tail =
        stat.size > HEAD_BYTES
          ? readChunk(full, Math.max(0, stat.size - TAIL_BYTES), TAIL_BYTES)
          : head;
    } catch {
      continue;
    }
    const ownSummary = collectSummaries(head, summariesByLeaf);
    candidates.push({ id, stat, head, tail, ownSummary });
  }

  // Pass 2: title each session — generated summary first, raw text fallback.
  const sessions = [];
  for (const { id, stat, head, tail, ownSummary } of candidates) {
    let summaryTitle = null;
    for (const uuid of tailUuids(tail)) {
      const summary = summariesByLeaf.get(uuid);
      if (summary) {
        summaryTitle = summary;
        break;
      }
    }
    summaryTitle = summaryTitle || ownSummary;
    const firstMsg = firstUserMessage(head);
    const title = summaryTitle || firstMsg;
    if (!title) continue; // empty/warmup sessions aren't worth resuming
    sessions.push({
      id,
      title: title.length > 100 ? title.slice(0, 100) + "…" : title,
      lastActiveAt: stat.mtime.toISOString(),
      // For callers that generate their own titles when the CLI hasn't:
      hasSummary: Boolean(summaryTitle),
      excerpt: firstMsg ? firstMsg.slice(0, 500) : null,
    });
  }
  return sessions.sort((a, b) => (a.lastActiveAt < b.lastActiveAt ? 1 : -1));
}

module.exports = { encodeProjectDir, extractSessionTitle, listSessions };
