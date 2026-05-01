# Intuiti — Card Scanner

A mobile web app that lets you snap a photo of a business card, extract the
contact information, and save it as a `.vcf` (vCard) file that your phone can
import natively.

Two extraction modes:

- **AI mode (recommended).** With an Anthropic API key, the app sends the image
  to Claude (`claude-opus-4-7`) and gets back a structured JSON response.
  Significantly more accurate on stylized fonts, logos, and color backgrounds.
- **On-device OCR.** Without a key, the app falls back to Tesseract.js running
  fully in the browser. No images leave the phone.

## Features

- Uses the phone camera (`<input capture="environment">`) — no native install.
- AI extraction via the [Claude API](https://docs.anthropic.com/) when a key is
  configured; client-side OCR via [Tesseract.js](https://tesseract.projectnaptha.com/)
  otherwise (or as fallback if the API call fails).
- Editable review form so you can fix any extraction mistakes before saving.
- Generates a vCard 3.0 file that iOS and Android open straight into Contacts.
- Installable as a PWA (Add to Home Screen).

## Enabling AI mode

1. Get a key at [console.anthropic.com](https://console.anthropic.com/) (it
   starts with `sk-ant-...`).
2. Open the app, expand **AI settings**, paste the key, tap **Save key**.
3. The mode indicator at the top changes to *Mode: AI (Claude)*.

The key is stored in your browser only (localStorage). To revoke, tap **Clear
key** in the settings panel — extraction switches back to on-device OCR.

> **Privacy:** in AI mode, images are sent to the Anthropic API for extraction.
> In on-device mode, nothing leaves your phone.

## Run it

It's a static site — three files, no build step.

### Locally

```bash
# from the project root
python3 -m http.server 8000
```

Then open `http://<your-laptop-ip>:8000` from your phone (must be on the same
Wi-Fi). Many browsers also require **HTTPS** for camera access on a real
device — see hosting options below.

### Hosting (recommended for phone use)

Camera capture works best from an HTTPS origin. Easiest options:

- **GitHub Pages** — push this repo, enable Pages on the branch, done.
- **Vercel / Netlify / Cloudflare Pages** — drop the folder in, deploy.

Once deployed, open the URL on your phone and tap **Add to Home Screen** to
install it as an app.

## How it works

1. The capture button opens the rear camera and returns a `File`.
2. **AI mode:** the image is downscaled to ≤1568px on the long edge, encoded as
   base64 JPEG, and sent to Claude with a JSON-schema response format. The
   reply is a structured object (name, title, org, phone, mobile, email,
   website, address).
   **OCR mode:** Tesseract.js runs locally and `parseCardText()` runs
   regex/heuristics to pull out the same fields.
3. The review form lets you edit anything before saving.
4. On save, a vCard 3.0 is built and downloaded — opening it on your phone
   prompts the system contacts app to import it.

If a Claude call fails (bad key, rate limit, network), the app automatically
falls back to on-device OCR for that scan and shows the reason in the status
line.

## Files

- `index.html` — markup and DOM
- `style.css` — mobile-first styling
- `app.js` — capture, OCR, parsing, vCard export
- `manifest.webmanifest` — PWA install metadata

## Tips for better scans

- Good, even lighting; avoid glare.
- Fill the frame with the card, parallel to the lens.
- Plain backgrounds help — dark cards on dark surfaces confuse OCR.
- If a field is wrong, just edit it before tapping **Save contact**.
