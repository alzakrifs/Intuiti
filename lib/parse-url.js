"use strict";

// Matches CSI sequences (colors, cursor movement), OSC sequences (window
// title etc.), and any remaining lone ESC + single char.
const ANSI_RE =
  /\x1b\[[0-9;?]*[ -/]*[@-~]|\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)|\x1b[ -/]*[0-~]/g;

function stripAnsi(str) {
  return str.replace(ANSI_RE, "").replace(/\r/g, "");
}

const SESSION_URL_RE = /https:\/\/claude\.ai\/code\/[\w-]+/;

// Charset kept permissive ([\w-]) so drift in the CLI's session id format
// doesn't break the match.
function extractSessionUrl(buffer) {
  const match = stripAnsi(buffer).match(SESSION_URL_RE);
  return match ? match[0] : null;
}

module.exports = { stripAnsi, extractSessionUrl };
