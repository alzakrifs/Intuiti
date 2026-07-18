#!/usr/bin/env node
"use strict";

// Simulates the claude CLI for testing the server without a real install.
// Usage: set config.json "claudeCmd": ["node", "test/fake-claude.js"]
// Pass --fail (before the args the server appends) to simulate a login error.

const args = process.argv.slice(2);

if (args.includes("--fail")) {
  process.stdout.write("\x1b[31mPlease log in: run /login in claude\x1b[0m\n");
  process.exit(1);
}

const nameIdx = args.indexOf("--remote-control") + 1;
const name = args[nameIdx] || "unnamed";
const resumeIdx = args.indexOf("--resume");
const resumed = resumeIdx !== -1 ? args[resumeIdx + 1] : null;
if (resumed) process.stdout.write(`Resuming session ${resumed}\r\n`);

process.stdout.write("\x1b]0;Claude Code\x07");
process.stdout.write(`\x1b[1m\x1b[35m Claude Code \x1b[0m starting "${name}"...\r\n`);

setTimeout(() => {
  process.stdout.write("  Remote control enabled.\r\n");
  process.stdout.write("  \x1b[4mhttps://claude.ai/code/session_test123\x1b[24m\r\n");
}, 1000);

setInterval(() => {}, 60_000); // stay alive like the real interactive CLI
