# Intuiti — Card Scanner

A mobile web app that lets you snap a photo of a business card, extract the
contact information with on-device OCR, and save it as a `.vcf` (vCard) file
that your phone can import natively.

Everything runs in the browser. No images leave the device.

## Features

- Uses the phone camera (`<input capture="environment">`) — no native install.
- Client-side OCR via [Tesseract.js](https://tesseract.projectnaptha.com/).
- Heuristic parser pulls out name, title, company, phones, email, website, and
  address.
- Editable review form so you can fix any OCR mistakes.
- Generates a vCard 3.0 file that iOS and Android open straight into Contacts.
- Installable as a PWA (Add to Home Screen).

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
2. Tesseract.js runs OCR locally and returns the recognized text.
3. `parseCardText()` runs regex/heuristics to pull out fields.
4. The review form lets you edit anything OCR got wrong.
5. On save, a vCard is built and downloaded — opening it on your phone
   prompts the system contacts app to import it.

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
