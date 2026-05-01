(function () {
  'use strict';

  const API_KEY_STORAGE = 'intuiti.anthropicApiKey';
  const CLAUDE_MODEL = 'claude-opus-4-7';
  const CLAUDE_API_URL = 'https://api.anthropic.com/v1/messages';
  const MAX_IMAGE_DIM = 1568;

  const captureSection = document.getElementById('capture-section');
  const previewSection = document.getElementById('preview-section');
  const formSection = document.getElementById('form-section');
  const cardInput = document.getElementById('card-input');
  const libraryInput = document.getElementById('library-input');
  const uploadButton = document.getElementById('upload-button');
  const preview = document.getElementById('preview');
  const status = document.getElementById('status');
  const progress = document.getElementById('progress');
  const retakeButton = document.getElementById('retake-button');
  const resetButton = document.getElementById('reset-button');
  const form = document.getElementById('contact-form');
  const apiKeyInput = document.getElementById('api-key-input');
  const saveKeyButton = document.getElementById('save-key-button');
  const clearKeyButton = document.getElementById('clear-key-button');
  const keyStatus = document.getElementById('key-status');
  const modeIndicator = document.getElementById('mode-indicator');
  const footerPrivacy = document.getElementById('footer-privacy');

  cardInput.addEventListener('change', handleFileSelected);
  libraryInput.addEventListener('change', handleFileSelected);
  uploadButton.addEventListener('click', () => libraryInput.click());
  retakeButton.addEventListener('click', resetApp);
  resetButton.addEventListener('click', resetApp);
  form.addEventListener('submit', handleSave);
  saveKeyButton.addEventListener('click', handleSaveKey);
  clearKeyButton.addEventListener('click', handleClearKey);

  initSettings();

  function setVisible(section, visible) {
    section.classList.toggle('hidden', !visible);
  }

  function resetApp() {
    cardInput.value = '';
    libraryInput.value = '';
    preview.removeAttribute('src');
    status.textContent = '';
    progress.value = 0;
    form.reset();
    setVisible(captureSection, true);
    setVisible(previewSection, false);
    setVisible(formSection, false);
  }

  async function handleFileSelected(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) return;

    const url = URL.createObjectURL(file);
    preview.src = url;

    setVisible(captureSection, false);
    setVisible(previewSection, true);
    setVisible(formSection, false);

    status.textContent = 'Preparing image...';
    progress.value = 0;

    const apiKey = getApiKey();

    try {
      let result;
      if (apiKey) {
        try {
          result = await extractWithClaude(file, apiKey);
        } catch (claudeErr) {
          console.error('Claude extraction failed, falling back to OCR:', claudeErr);
          status.textContent = 'AI extraction failed (' + claudeErr.message + '). Using on-device OCR...';
          result = await extractWithTesseract(file);
          result.source = 'tesseract-fallback';
        }
      } else {
        result = await extractWithTesseract(file);
      }
      populateForm(result.fields, result.rawText);
      setVisible(formSection, true);
      status.textContent = sourceMessage(result.source);
    } catch (err) {
      console.error(err);
      status.textContent = 'Could not read the card. Try again with better lighting.';
    }
  }

  function sourceMessage(source) {
    switch (source) {
      case 'claude':
        return 'Done (AI extraction). Review the details below.';
      case 'tesseract-fallback':
        return 'Done (on-device fallback). Review the details below.';
      default:
        return 'Done. Review the details below.';
    }
  }

  async function extractWithTesseract(file) {
    const text = await runOCR(file);
    const fields = parseCardText(text);
    return { fields, rawText: text, source: 'tesseract' };
  }

  async function extractWithClaude(file, apiKey) {
    status.textContent = 'Resizing image...';
    progress.value = 0.1;
    const { base64, mediaType } = await prepareImageForClaude(file);

    status.textContent = 'Asking Claude to read the card...';
    progress.value = 0.4;

    const body = {
      model: CLAUDE_MODEL,
      max_tokens: 1024,
      system:
        'You extract structured contact information from photos of business cards. ' +
        'Return only fields visible on the card. Use empty strings for missing fields. ' +
        'Preserve phone numbers in their original format. ' +
        'For "phone", use the main work/office number. For "mobile", use the cell/mobile number if labeled separately. ' +
        'Combine multi-line addresses into one comma-separated string.',
      messages: [
        {
          role: 'user',
          content: [
            { type: 'image', source: { type: 'base64', media_type: mediaType, data: base64 } },
            { type: 'text', text: 'Extract the contact information from this business card.' },
          ],
        },
      ],
      output_config: {
        format: {
          type: 'json_schema',
          schema: {
            type: 'object',
            properties: {
              firstName: { type: 'string' },
              lastName: { type: 'string' },
              title: { type: 'string' },
              org: { type: 'string' },
              phone: { type: 'string' },
              mobile: { type: 'string' },
              email: { type: 'string' },
              website: { type: 'string' },
              address: { type: 'string' },
            },
            required: [
              'firstName', 'lastName', 'title', 'org',
              'phone', 'mobile', 'email', 'website', 'address',
            ],
            additionalProperties: false,
          },
        },
      },
    };

    const response = await fetch(CLAUDE_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-dangerous-direct-browser-access': 'true',
      },
      body: JSON.stringify(body),
    });

    progress.value = 0.85;

    if (!response.ok) {
      let detail = '';
      try {
        const errBody = await response.json();
        detail = (errBody && errBody.error && errBody.error.message) || '';
      } catch (_) { /* ignore */ }
      throw new Error('HTTP ' + response.status + (detail ? ': ' + detail : ''));
    }

    const data = await response.json();
    const text = (data.content || [])
      .filter((b) => b.type === 'text')
      .map((b) => b.text)
      .join('')
      .trim();

    if (!text) {
      throw new Error('Empty response from Claude');
    }

    let fields;
    try {
      fields = JSON.parse(text);
    } catch (e) {
      throw new Error('Could not parse JSON response');
    }

    progress.value = 1;
    return { fields: normalizeFields(fields), rawText: text, source: 'claude' };
  }

  function normalizeFields(raw) {
    const keys = ['firstName', 'lastName', 'title', 'org', 'phone', 'mobile', 'email', 'website', 'address'];
    const out = {};
    for (const k of keys) {
      out[k] = typeof raw[k] === 'string' ? raw[k].trim() : '';
    }
    return out;
  }

  async function prepareImageForClaude(file) {
    const bitmap = await createImageBitmap(file);
    const longest = Math.max(bitmap.width, bitmap.height);
    const scale = longest > MAX_IMAGE_DIM ? MAX_IMAGE_DIM / longest : 1;
    const w = Math.round(bitmap.width * scale);
    const h = Math.round(bitmap.height * scale);

    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(bitmap, 0, 0, w, h);
    bitmap.close && bitmap.close();

    const blob = await new Promise((resolve, reject) => {
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('Could not encode image'))),
        'image/jpeg',
        0.85,
      );
    });

    const buffer = await blob.arrayBuffer();
    return { base64: arrayBufferToBase64(buffer), mediaType: 'image/jpeg' };
  }

  function arrayBufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  function initSettings() {
    const stored = getApiKey();
    if (stored) {
      apiKeyInput.value = stored;
    }
    updateModeIndicator();
  }

  function getApiKey() {
    try {
      return localStorage.getItem(API_KEY_STORAGE) || '';
    } catch (_) {
      return '';
    }
  }

  function handleSaveKey() {
    const value = apiKeyInput.value.trim();
    if (!value) {
      setKeyStatus('Enter a key first.', 'err');
      return;
    }
    if (!/^sk-ant-/.test(value)) {
      setKeyStatus('That does not look like an Anthropic key (expected sk-ant-...).', 'err');
      return;
    }
    try {
      localStorage.setItem(API_KEY_STORAGE, value);
      setKeyStatus('Saved. AI mode is on.', 'ok');
      updateModeIndicator();
    } catch (e) {
      setKeyStatus('Could not save key: ' + e.message, 'err');
    }
  }

  function handleClearKey() {
    try {
      localStorage.removeItem(API_KEY_STORAGE);
    } catch (_) { /* ignore */ }
    apiKeyInput.value = '';
    setKeyStatus('Cleared. Falling back to on-device OCR.', 'ok');
    updateModeIndicator();
  }

  function setKeyStatus(message, kind) {
    keyStatus.textContent = message;
    keyStatus.classList.remove('ok', 'err');
    if (kind) keyStatus.classList.add(kind);
  }

  function updateModeIndicator() {
    const aiOn = !!getApiKey();
    modeIndicator.textContent = aiOn ? 'Mode: AI (Claude)' : 'Mode: on-device OCR';
    modeIndicator.classList.toggle('ai-on', aiOn);
    if (footerPrivacy) {
      footerPrivacy.textContent = aiOn
        ? 'AI mode: images are sent to the Anthropic API for extraction.'
        : 'OCR runs in your browser. No images leave your phone.';
    }
  }

  async function runOCR(file) {
    if (typeof Tesseract === 'undefined') {
      throw new Error('OCR engine failed to load. Check your connection.');
    }
    const result = await Tesseract.recognize(file, 'eng', {
      logger: (m) => {
        if (m.status) {
          status.textContent = capitalize(m.status) + (m.progress ? '...' : '');
        }
        if (typeof m.progress === 'number') {
          progress.value = m.progress;
        }
      },
    });
    return result.data.text || '';
  }

  function capitalize(s) {
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  function parseCardText(rawText) {
    const lines = rawText
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter(Boolean);

    const fields = {
      firstName: '',
      lastName: '',
      title: '',
      org: '',
      phone: '',
      mobile: '',
      email: '',
      website: '',
      address: '',
    };

    const consumed = new Set();

    // Email
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    for (let i = 0; i < lines.length; i++) {
      const m = lines[i].match(emailRegex);
      if (m) {
        fields.email = m[0].toLowerCase();
        consumed.add(i);
        break;
      }
    }

    // Website (skip emails)
    const urlRegex = /\b((https?:\/\/)?(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)+(\/[^\s]*)?)\b/i;
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      if (lines[i].includes('@')) continue;
      const m = lines[i].match(urlRegex);
      if (m && !/\.(jpg|png|gif|pdf)$/i.test(m[1])) {
        fields.website = normalizeUrl(m[1]);
        consumed.add(i);
        break;
      }
    }

    // Phones (collect up to two)
    const phoneRegex = /(\+?\d[\d\s().-]{7,}\d)/g;
    const foundPhones = [];
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      const matches = lines[i].match(phoneRegex);
      if (!matches) continue;
      for (const raw of matches) {
        const digits = raw.replace(/\D/g, '');
        if (digits.length < 7 || digits.length > 15) continue;
        const isMobile = /\b(m|mob|mobile|cell|c)\b[\s.:]/i.test(lines[i]) || /^m[\s.:]/i.test(lines[i]);
        foundPhones.push({ raw: raw.trim(), isMobile, lineIdx: i });
      }
      consumed.add(i);
      if (foundPhones.length >= 2) break;
    }
    const mobile = foundPhones.find((p) => p.isMobile);
    const main = foundPhones.find((p) => p !== mobile);
    if (mobile) fields.mobile = mobile.raw;
    if (main) fields.phone = main.raw;
    if (!main && !mobile && foundPhones[0]) fields.phone = foundPhones[0].raw;

    // Company: look for legal suffixes
    const orgRegex = /\b(inc\.?|llc|ltd\.?|gmbh|s\.?a\.?|co\.?|corp\.?|company|group|studio|labs|technologies|solutions|consulting|partners|holdings|agency)\b/i;
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      if (orgRegex.test(lines[i])) {
        fields.org = lines[i].replace(/[|•·]+$/g, '').trim();
        consumed.add(i);
        break;
      }
    }

    // Job title heuristics
    const titleRegex = /\b(ceo|cto|cfo|coo|cmo|founder|co-founder|president|vp|vice president|director|manager|engineer|developer|designer|architect|consultant|analyst|specialist|officer|head of|lead|principal|associate|coordinator|administrator|owner|partner|advisor|strategist|producer|editor|writer|sales|marketing|product|hr|account)\b/i;
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      if (titleRegex.test(lines[i]) && lines[i].length < 60) {
        fields.title = lines[i];
        consumed.add(i);
        break;
      }
    }

    // Name: prefer the earliest unconsumed line that looks like a person's name
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      if (looksLikeName(lines[i])) {
        const parts = lines[i].split(/\s+/).filter(Boolean);
        fields.firstName = parts[0] || '';
        fields.lastName = parts.slice(1).join(' ');
        consumed.add(i);
        break;
      }
    }

    // Address: longest remaining line containing digits, or lines with common address words
    const addressLines = [];
    const addressKeywords = /\b(street|st\.?|avenue|ave\.?|road|rd\.?|blvd|boulevard|suite|ste\.?|floor|fl\.?|drive|dr\.?|lane|ln\.?|way|court|ct\.?|po box|p\.o\. box)\b/i;
    for (let i = 0; i < lines.length; i++) {
      if (consumed.has(i)) continue;
      if (addressKeywords.test(lines[i]) || /\b\d{5}(-\d{4})?\b/.test(lines[i])) {
        addressLines.push(lines[i]);
        consumed.add(i);
      }
    }
    if (addressLines.length) {
      fields.address = addressLines.join(', ');
    }

    return fields;
  }

  function looksLikeName(line) {
    if (line.length > 50) return false;
    if (/\d/.test(line)) return false;
    if (/[@/]/.test(line)) return false;
    const words = line.split(/\s+/).filter(Boolean);
    if (words.length < 2 || words.length > 4) return false;
    const wordPattern = /^[A-Z][A-Za-zÀ-ÖØ-öø-ÿ'’.-]+$/;
    const allCapsPattern = /^[A-ZÀ-Ö'’.-]+$/;
    return words.every((w) => wordPattern.test(w) || allCapsPattern.test(w));
  }

  function normalizeUrl(u) {
    if (!u) return '';
    if (/^https?:\/\//i.test(u)) return u;
    return 'https://' + u;
  }

  function populateForm(fields, rawText) {
    for (const key of Object.keys(fields)) {
      const input = form.elements.namedItem(key);
      if (input) input.value = fields[key];
    }
    const notes = form.elements.namedItem('notes');
    if (notes) notes.value = rawText.trim();
  }

  function handleSave(event) {
    event.preventDefault();
    const data = collectFormData();
    if (!hasAnyContent(data)) {
      status.textContent = 'Please fill in at least one field.';
      return;
    }
    const vcard = buildVCard(data);
    downloadVCard(vcard, contactFileName(data));
    status.textContent = 'Contact file ready. Open it to add to your contacts.';
  }

  function collectFormData() {
    const data = {};
    for (const el of form.elements) {
      if (el.name) data[el.name] = el.value.trim();
    }
    return data;
  }

  function hasAnyContent(data) {
    return ['firstName', 'lastName', 'org', 'phone', 'mobile', 'email'].some((k) => data[k]);
  }

  function escapeVCard(value) {
    return String(value || '')
      .replace(/\\/g, '\\\\')
      .replace(/\n/g, '\\n')
      .replace(/,/g, '\\,')
      .replace(/;/g, '\\;');
  }

  function buildVCard(d) {
    const lines = ['BEGIN:VCARD', 'VERSION:3.0'];
    const fullName = [d.firstName, d.lastName].filter(Boolean).join(' ').trim();
    if (fullName || d.org) {
      lines.push('FN:' + escapeVCard(fullName || d.org));
    }
    if (d.firstName || d.lastName) {
      lines.push('N:' + escapeVCard(d.lastName) + ';' + escapeVCard(d.firstName) + ';;;');
    }
    if (d.org) lines.push('ORG:' + escapeVCard(d.org));
    if (d.title) lines.push('TITLE:' + escapeVCard(d.title));
    if (d.phone) lines.push('TEL;TYPE=WORK,VOICE:' + escapeVCard(d.phone));
    if (d.mobile) lines.push('TEL;TYPE=CELL,VOICE:' + escapeVCard(d.mobile));
    if (d.email) lines.push('EMAIL;TYPE=INTERNET:' + escapeVCard(d.email));
    if (d.website) lines.push('URL:' + escapeVCard(d.website));
    if (d.address) lines.push('ADR;TYPE=WORK:;;' + escapeVCard(d.address) + ';;;;');
    if (d.notes) lines.push('NOTE:' + escapeVCard(d.notes));
    lines.push('REV:' + new Date().toISOString());
    lines.push('END:VCARD');
    return lines.join('\r\n');
  }

  function contactFileName(d) {
    const base = [d.firstName, d.lastName].filter(Boolean).join('-') || d.org || 'contact';
    const safe = base.replace(/[^A-Za-z0-9-_]+/g, '_');
    return safe + '.vcf';
  }

  function downloadVCard(text, filename) {
    const blob = new Blob([text], { type: 'text/vcard;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1500);
  }
})();
