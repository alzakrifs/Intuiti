"use strict";

const { test } = require("node:test");
const assert = require("node:assert");
const { stripAnsi, extractSessionUrl } = require("../lib/parse-url.js");

test("stripAnsi removes CSI color codes", () => {
  assert.strictEqual(stripAnsi("\x1b[32mgreen\x1b[0m text"), "green text");
});

test("stripAnsi removes cursor movement sequences", () => {
  assert.strictEqual(stripAnsi("\x1b[2J\x1b[1;1Hhello\x1b[K"), "hello");
});

test("stripAnsi removes OSC title sequences", () => {
  assert.strictEqual(stripAnsi("\x1b]0;my title\x07body"), "body");
  assert.strictEqual(stripAnsi("\x1b]8;;http://x\x1b\\link"), "link");
});

test("stripAnsi removes carriage returns and lone escapes", () => {
  assert.strictEqual(stripAnsi("line1\r\nline2\x1b7"), "line1\nline2");
});

test("extractSessionUrl finds URL in ANSI-decorated TUI output", () => {
  const out =
    "\x1b[1m\x1b[35m Claude Code \x1b[0m\r\n" +
    "  Remote control enabled.\r\n" +
    "  \x1b[4mhttps://claude.ai/code/session_01AbCdEf\x1b[24m\r\n";
  assert.strictEqual(
    extractSessionUrl(out),
    "https://claude.ai/code/session_01AbCdEf"
  );
});

test("extractSessionUrl handles URL split across concatenated chunks", () => {
  const chunk1 = "connect at https://claude.ai/co";
  const chunk2 = "de/session_xyz-123 to continue";
  assert.strictEqual(extractSessionUrl(chunk1), null);
  assert.strictEqual(
    extractSessionUrl(chunk1 + chunk2),
    "https://claude.ai/code/session_xyz-123"
  );
});

test("extractSessionUrl returns null when no URL present", () => {
  assert.strictEqual(extractSessionUrl("no url here\x1b[0m"), null);
  assert.strictEqual(extractSessionUrl(""), null);
});

test("extractSessionUrl does not swallow trailing punctuation", () => {
  assert.strictEqual(
    extractSessionUrl("(https://claude.ai/code/session_abc)."),
    "https://claude.ai/code/session_abc"
  );
  assert.strictEqual(
    extractSessionUrl("│ https://claude.ai/code/session_abc │"),
    "https://claude.ai/code/session_abc"
  );
});

test("extractSessionUrl ignores other claude.ai URLs", () => {
  assert.strictEqual(extractSessionUrl("see https://claude.ai/login"), null);
});
