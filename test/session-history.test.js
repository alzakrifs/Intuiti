"use strict";

const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {
  encodeProjectDir,
  extractSessionTitle,
  listSessions,
} = require("../lib/session-history.js");

test("encodeProjectDir replaces non-alphanumerics with dashes", () => {
  assert.strictEqual(encodeProjectDir("/home/user/Intuiti"), "-home-user-Intuiti");
  assert.strictEqual(
    encodeProjectDir("C:\\Users\\Fahad\\claude-workspace"),
    "C--Users-Fahad-claude-workspace"
  );
});

test("extractSessionTitle prefers a summary entry", () => {
  const jsonl = [
    JSON.stringify({ type: "summary", summary: "Fixing the login bug" }),
    JSON.stringify({ type: "user", message: { role: "user", content: "hello" } }),
  ].join("\n");
  assert.strictEqual(extractSessionTitle(jsonl), "Fixing the login bug");
});

test("extractSessionTitle falls back to first plain user message", () => {
  const jsonl = [
    JSON.stringify({ type: "queue-operation", content: "queued text" }),
    JSON.stringify({ type: "user", message: { role: "user", content: "<command-name>/foo</command-name>" } }),
    JSON.stringify({ type: "user", message: { role: "user", content: "build me an app" } }),
  ].join("\n");
  assert.strictEqual(extractSessionTitle(jsonl), "build me an app");
});

test("extractSessionTitle survives malformed and truncated lines", () => {
  const jsonl = 'not json at all\n{"type":"user","message":{"content":"real question"}}\n{"trunca';
  assert.strictEqual(extractSessionTitle(jsonl), "real question");
});

test("extractSessionTitle returns null when nothing usable", () => {
  assert.strictEqual(extractSessionTitle(""), null);
  assert.strictEqual(
    extractSessionTitle(JSON.stringify({ type: "user", message: { content: "Caveat: injected" } })),
    null
  );
});

test("listSessions reads sessions from an encoded project dir", () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "intuiti-test-"));
  const projectPath = "C:\\Users\\test\\demo";
  const dir = path.join(tmp, "projects", encodeProjectDir(projectPath));
  fs.mkdirSync(dir, { recursive: true });
  const id = "11111111-2222-3333-4444-555555555555";
  fs.writeFileSync(
    path.join(dir, `${id}.jsonl`),
    JSON.stringify({ type: "user", message: { content: "first task" } }) + "\n"
  );
  fs.writeFileSync(path.join(dir, "not-a-session.txt"), "ignore me");
  fs.writeFileSync(
    path.join(dir, "22222222-2222-3333-4444-555555555555.jsonl"),
    "{}\n" // no user message -> excluded
  );

  const sessions = listSessions(projectPath, tmp);
  assert.strictEqual(sessions.length, 1);
  assert.strictEqual(sessions[0].id, id);
  assert.strictEqual(sessions[0].title, "first task");
  assert.ok(sessions[0].lastActiveAt);

  fs.rmSync(tmp, { recursive: true, force: true });
});

test("listSessions returns empty for unknown project dir", () => {
  assert.deepStrictEqual(listSessions("/nope/nothing", "/nonexistent-claude-dir"), []);
});
