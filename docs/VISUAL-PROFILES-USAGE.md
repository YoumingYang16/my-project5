# Visual profiles and migration

## Single-paper form

Open `http://127.0.0.1:5679/form/paper-image-form`.

Recommended website settings:

- Output use: Website header
- Visual style: Object close-up, Application scene, or Automatic
- Aesthetic character: Natural realistic or Minimal premium
- Cover title: Image only
- Quality mode: Strict

Recommended Xiaohongshu settings:

- Output use: Xiaohongshu cover
- Visual style: Application scene or Documentary research
- Aesthetic character: Vivid social, Warm humanistic, or Automatic
- Cover title: Show title
- Quality mode: Strict

## Batch generation

The al-folio adapter supports the same profile IDs:

```powershell
node scripts/generate-alfolio-candidates.mjs --all --provider=dual --quality=strict --output=website-header --style=object-closeup --aesthetic=natural-realistic
```

Available output values:

- `website-header`
- `xiaohongshu-cover`

Available style values:

- `auto`
- `documentary-research`
- `object-closeup`
- `application-scene`
- `academic-editorial`
- `architectural-space`
- `concise-method`

Available aesthetic values:

- `auto`
- `natural-realistic`
- `clear-technical`
- `warm-humanistic`
- `minimal-premium`
- `vivid-social`

## Website migration

The generation core does not depend on al-folio. A website adapter only needs to:

1. Read the site's paper metadata and PDF path.
2. Send the stable webhook fields, including optional profile IDs.
3. Save returned candidate files inside the repository.
4. Write the selected path into the website's native image field.

For al-folio, the native field remains `preview` and selected images remain in `assets/img/publication_preview/`. No migration change is required.

## Copywriting integration

Both image generation and future copywriting use `communicationBrief` version `1.0`. The copywriting module should consume:

- audience and communication goal;
- core message and evidence;
- limitations and prohibited claim language;
- cover headline when a shared image-text concept is desired.

The copywriting module should not consume provider prompts, seeds, model names, or image-quality fields. Those remain private to the visual module. This separation lets either module change tools without changing the shared research record.

## Backward compatibility

- Old webhook requests still default to `website-header`, `auto` style, and `auto` aesthetic.
- Existing BibTeX entries do not require new fields.
- Optional BibTeX fields are `outputprofile`, `visualstyle`, `aestheticprofile`, `communicationgoal`, `audience`, and `coverheadline`.
- The workflow ID and webhook path are unchanged.

## IP-Adapter and Satori

- `referenceMode=auto`: use IP-Adapter only when a PDF figure is classified as a suitable research photo, device, artifact, or building image.
- `referenceMode=required`: require a suitable reference image; use this for technical verification or papers whose object identity must be preserved.
- `referenceMode=off`: use text-to-image generation only.
- IP-Adapter runs inside the existing ComfyUI provider. Successful candidates report `providerStrategy: ip-adapter`, `referenceSource`, and `referenceStrength`.
- If no suitable reference image exists in `auto` mode, the workflow safely falls back to ordinary ComfyUI generation.
- Xiaohongshu title layout now uses the actual Vercel Satori library with the bundled open-source Noto Sans CJK SC font. The existing `/render/xhs-cover` interface is unchanged.
- Website headers remain text-free `900x600` images. Xiaohongshu covers are `1242x1660` and use Satori only for deterministic title and provenance layout.
- Image generation and future copy generation share `communicationBrief`. Image-only fields remain under `communicationBrief.visual`, so a text module can reuse audience, communication goal, evidence, limitations, and forbidden claims without depending on ComfyUI or Satori.