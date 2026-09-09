import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import express from 'express';
import sharp from 'sharp';
import slugify from 'slugify';
import { renderFallbackBackground, renderXhsCover, safeCoverHeadline, XHS_COVER_SIZE } from './xhs-cover.js';

const app = express();
const port = Number(process.env.PORT || 3000);
const assetsDir = process.env.ASSETS_DIR || path.resolve('assets');
const assetsPublicUrl = (process.env.ASSETS_PUBLIC_URL || 'http://localhost:8088/assets').replace(/\/$/, '');
const comfyuiBaseUrl = (process.env.COMFYUI_BASE_URL || 'http://host.docker.internal:8188').replace(/\/$/, '');
const comfyuiCheckpoint = process.env.COMFYUI_CHECKPOINT || 'RealVisXL_V5.0_fp16.safetensors';
const comfyuiTimeoutMs = Number(process.env.COMFYUI_TIMEOUT_MS || 600000);
const comfyuiSteps = Math.max(22, Math.min(Number(process.env.COMFYUI_STEPS || 30), 46));
const comfyuiCfg = Math.max(3, Math.min(Number(process.env.COMFYUI_CFG || 4.2), 7));
const imageProvider = String(process.env.IMAGE_PROVIDER || 'qwen-image').trim().toLowerCase();
const qwenApiKey = String(process.env.QWEN_IMAGE_API_KEY || process.env.DASHSCOPE_API_KEY || '').trim();
const qwenApiUrl = String(
  process.env.QWEN_IMAGE_API_URL
  || 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation',
).trim();
const qwenImageModel = String(process.env.QWEN_IMAGE_MODEL || 'qwen-image-2.0-pro').trim();
const generationVersion = String(process.env.GENERATION_VERSION || 'v3-xhs-evidence').trim();
const maxPdfPages = Math.max(24, Math.min(Number(process.env.PDF_MAX_PAGES || 120), 240));
const maxPdfSourceFigures = Math.max(12, Math.min(Number(process.env.PDF_MAX_SOURCE_FIGURES || 48), 80));
const maxReferenceCandidates = 5;
const maxQwenReferenceImages = 3;
const ipAdapterEnabled = String(process.env.IPADAPTER_ENABLED || 'true').toLowerCase() !== 'false';
const ipAdapterPreset = process.env.IPADAPTER_PRESET || 'STANDARD (medium strength)';
const ipAdapterDefaultStrength = Math.max(0.35, Math.min(Number(process.env.IPADAPTER_STRENGTH || 0.6), 0.8));
const ocrEnabled = String(process.env.OCR_ENABLED || 'true').toLowerCase() !== 'false';
const localVisionEnabled = String(process.env.LOCAL_VISION_ENABLED || 'true').toLowerCase() !== 'false';
const visionModel = process.env.VISION_MODEL || 'Xenova/clip-vit-base-patch32';
const modelCacheDir = process.env.MODEL_CACHE_DIR || '/data/model-cache';
const ocrCacheDir = process.env.OCR_CACHE_DIR || '/data/ocr-cache';
let ocrWorkerPromise;
let visionPipelinesPromise;
let pdfJsPromise;

const OUTPUT_PROFILES = {
  'website-header': {
    width: 900,
    height: 600,
    instruction: 'horizontal 3:2 publication thumbnail, one clear focal subject, readable at small size, no title area required',
  },
  'xiaohongshu-cover': {
    width: 1242,
    height: 1660,
    instruction: 'vertical 3:4 science communication cover, one clear focal subject, reserve calm lower space for later programmatic Chinese title',
  },
};

const AESTHETIC_DIRECTIONS = {
  auto: 'restrained academic editorial quality with coherent color and lighting',
  'natural-realistic': 'soft natural light, truthful materials, low saturation, documentary color, physically plausible scene',
  'clear-technical': 'clean neutral background, crisp object separation, cool white light, precise equipment silhouette',
  'warm-humanistic': 'warm daylight, tactile cultural materials, calm human-scale atmosphere, restrained earthy accents',
  'minimal-premium': 'large negative space, one focal object, refined editorial lighting, quiet white or light gray background',
  'vivid-social': 'clear color contrast, immediate focal point, lively but credible science communication aesthetic, uncluttered composition',
};

app.use('/extract/pdf', express.raw({ type: ['application/pdf', 'application/octet-stream'], limit: '35mb' }));
app.use(express.json({ limit: '20mb' }));
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') {
    res.sendStatus(204);
    return;
  }
  next();
});

const palette = {
  ink: '#252a31',
  muted: '#5d6773',
  paper: '#f7f9fb',
  border: '#d9dee5',
  blue: '#6f9ead',
  green: '#85ae8b',
  amber: '#d8b470',
  rose: '#c88989',
  violet: '#a994bd',
};

function esc(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function stableId(input) {
  return crypto.createHash('sha256').update(JSON.stringify(input)).digest('hex').slice(0, 12);
}

function safeSlug(value, fallback = 'image') {
  const slug = slugify(String(value || ''), { lower: true, strict: true, trim: true });
  const normalized = slug || fallback;
  const maxLength = 72;
  if (normalized.length <= maxLength) return normalized;
  const digest = crypto.createHash('sha256').update(normalized).digest('hex').slice(0, 8);
  return `${normalized.slice(0, maxLength - digest.length - 1).replace(/-+$/, '')}-${digest}`;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 30000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function readJsonResponse(response, label) {
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${label} returned ${response.status}: ${text.slice(0, 500)}`);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`${label} returned invalid JSON: ${text.slice(0, 500)}`);
  }
}

function normalizeWhitespace(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

function uniqueStrings(values, limit = Infinity) {
  const seen = new Set();
  const result = [];
  for (const value of values.flat(Infinity)) {
    const textValue = normalizeWhitespace(value);
    if (!textValue) continue;
    const key = textValue.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(textValue);
    if (result.length >= limit) break;
  }
  return result;
}

function captionFromPageText(pageText) {
  const textValue = normalizeWhitespace(pageText);
  const matches = textValue.match(/fig(?:ure)?\.?\s*\d+[a-z]?[:.\s-]+.{0,240}?(?=(?:fig(?:ure)?\.?\s*\d+|table\s*\d+|$))/ig) || [];
  return normalizeWhitespace(matches[0] || '').slice(0, 280);
}

function cleanExtractedPdfText(value) {
  let textValue = String(value || '')
    .replace(/\u0000/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  const referencesIndex = textValue.search(/\n\s*(references|bibliography)\s*\n/i);
  if (referencesIndex > 1200) textValue = textValue.slice(0, referencesIndex);
  return textValue.slice(0, 80000);
}

async function ensurePdfJs() {
  pdfJsPromise ||= import('pdfjs-dist/legacy/build/pdf.mjs');
  return pdfJsPromise;
}

async function getPdfObject(store, id) {
  if (!store || !id) return null;
  try {
    const direct = store.get(id);
    if (direct) return direct;
  } catch {}
  try {
    return await new Promise((resolve) => {
      const timer = setTimeout(() => resolve(null), 1200);
      store.get(id, (value) => {
        clearTimeout(timer);
        resolve(value || null);
      });
    });
  } catch {
    return null;
  }
}

function rawImageChannels(image) {
  const width = Number(image?.width || 0);
  const height = Number(image?.height || 0);
  const data = image?.data;
  if (!width || !height || !data?.length) return null;
  const pixels = width * height;
  if (data.length === pixels * 4) return 4;
  if (data.length === pixels * 3) return 3;
  if (data.length === pixels) return 1;
  return null;
}

async function pdfImageToJpegBuffer(image) {
  const width = Number(image?.width || 0);
  const height = Number(image?.height || 0);
  const channels = rawImageChannels(image);
  if (!channels || width < 180 || height < 110) return null;
  return sharp(Buffer.from(image.data), { raw: { width, height, channels } })
    .rotate()
    .jpeg({ quality: 90, mozjpeg: true })
    .toBuffer();
}

async function scorePdfFigure(buffer, pageNumber, caption) {
  const metadata = await sharp(buffer).metadata();
  const width = Number(metadata.width || 0);
  const height = Number(metadata.height || 0);
  const area = width * height;
  const aspect = width && height ? width / height : 0;
  const dimensionScore = Math.min(1, area / (720 * 420));
  const aspectScore = aspect >= 1.1 && aspect <= 2.2 ? 1 : aspect >= 0.72 && aspect <= 3.2 ? 0.7 : 0.35;
  const pageScore = pageNumber <= 10 ? 1 : 0.78;
  const captionScore = caption ? 1 : 0.45;
  return Number((0.45 * dimensionScore + 0.28 * aspectScore + 0.12 * pageScore + 0.15 * captionScore).toFixed(2));
}

function captionFigureType(caption = '') {
  const value = normalizeWhitespace(caption).toLowerCase();
  if (/\b(table|confusion matrix|ablation|accuracy|auc|plot|graph|chart|distribution|boxplot)\b/.test(value)) return 'chart-table';
  if (/\b(methodology|procedure|pipeline|workflow|framework|diagram|overview|flowchart|schematic|system architecture|network architecture|model architecture)\b/.test(value)) return 'diagram';
  if (/\b(interface|screen|screenshot|dashboard|prototype ui|user interface)\b/.test(value)) return 'interface';
  if (/\b(logo|icon|badge|qr code)\b/.test(value)) return 'logo-decoration';
  if (/\b(device|prototype|sensor|headset|scanner|artifact|building|heritage|museum|equipment|historic(?:al)? architecture|architectural (?:site|building|structure)|gate|pavilion|timber|dou-gong|bracket|roof)\b/.test(value)) return 'artifact-device';
  if (/\b(participant|visitor|field study|experiment|workshop|interview|co-creation|recording)\b/.test(value)) return 'research-photo';
  return 'unknown';
}

async function rankPdfFigurePool(figures, limit = maxPdfSourceFigures) {
  const preselected = [...figures]
    .sort((a, b) => b.sourceScore - a.sourceScore)
    .slice(0, Math.max(limit, 80));
  const assessed = [];
  for (const figure of preselected) {
    let classification;
    try {
      classification = await classifyReferenceImage(figure.buffer, {
        caption: figure.caption,
        width: figure.width,
        height: figure.height,
        sourceScore: figure.sourceScore,
        relevanceScore: figure.sourceScore,
      });
    } catch (error) {
      classification = {
        imageType: captionFigureType(figure.caption),
        referenceScore: 0,
        referenceEligible: false,
        referenceWarnings: [`Reference assessment failed: ${String(error.message || error).slice(0, 160)}`],
      };
    }
    assessed.push({ ...figure, ...classification, referenceAssessed: true });
  }
  assessed.sort((a, b) => {
    const eligibleDelta = Number(Boolean(b.referenceEligible)) - Number(Boolean(a.referenceEligible));
    if (eligibleDelta) return eligibleDelta;
    const referenceDelta = Number(b.referenceScore || 0) - Number(a.referenceScore || 0);
    if (referenceDelta) return referenceDelta;
    return Number(b.sourceScore || 0) - Number(a.sourceScore || 0);
  });
  return assessed.slice(0, limit);
}

async function extractPdfPackage(buffer, paperId = 'paper') {
  if (!Buffer.isBuffer(buffer) || buffer.length < 100) throw new Error('A non-empty PDF file is required');
  const pdf = await ensurePdfJs();
  const task = pdf.getDocument({
    data: new Uint8Array(buffer),
    disableFontFace: true,
    useSystemFonts: true,
    isEvalSupported: false,
  });
  const document = await task.promise;
  const pageCount = document.numPages;
  const pages = [];
  const figures = [];
  const seen = new Set();
  try {
    const maxPages = Math.min(document.numPages, maxPdfPages);
    for (let pageNumber = 1; pageNumber <= maxPages; pageNumber += 1) {
      const page = await document.getPage(pageNumber);
      const textContent = await page.getTextContent();
      const pageText = normalizeWhitespace(textContent.items.map((item) => item.str || '').join(' '));
      pages.push(pageText);
      const caption = captionFromPageText(pageText);
      if (pageNumber <= maxPages) {
        const operatorList = await page.getOperatorList();
        for (let index = 0; index < operatorList.fnArray.length; index += 1) {
          const fn = operatorList.fnArray[index];
          const args = operatorList.argsArray[index] || [];
          const named = [pdf.OPS.paintImageXObject, pdf.OPS.paintImageXObjectRepeat].includes(fn);
          const inline = [pdf.OPS.paintInlineImageXObject, pdf.OPS.paintInlineImageXObjectGroup].includes(fn);
          if (!named && !inline) continue;
          const image = inline
            ? args[0]
            : await getPdfObject(page.objs, args[0]) || await getPdfObject(page.commonObjs, args[0]);
          const imageBuffer = await pdfImageToJpegBuffer(image);
          if (!imageBuffer) continue;
          const hash = crypto.createHash('sha256').update(imageBuffer).digest('hex').slice(0, 20);
          if (seen.has(hash)) continue;
          seen.add(hash);
          const sourceScore = await scorePdfFigure(imageBuffer, pageNumber, caption);
          if (sourceScore < 0.45) continue;
          figures.push({
            buffer: imageBuffer,
            hash,
            pageNumber,
            caption,
            sourceScore,
            width: Number(image.width || 0),
            height: Number(image.height || 0),
          });
        }
      }
      page.cleanup();
    }
  } finally {
    await document.destroy();
  }
  await ensureAssetsDir();
  const selectedFigures = await rankPdfFigurePool(figures, maxPdfSourceFigures);
  const sourceFigures = [];
  for (const [index, figure] of selectedFigures.entries()) {
    const fileName = `${safeSlug(paperId, 'paper')}-source-page-${figure.pageNumber}-${index + 1}-${figure.hash.slice(0, 8)}.jpg`;
    await sharp(figure.buffer)
      .resize(2000, 1600, { fit: 'inside', withoutEnlargement: true })
      .jpeg({ quality: 92, mozjpeg: true })
      .toFile(path.join(assetsDir, fileName));
    sourceFigures.push({
      id: `source-${figure.pageNumber}-${index + 1}`,
      type: 'source-extracted',
      url: `${assetsPublicUrl}/${fileName}`,
      fileName,
      pageNumber: figure.pageNumber,
      caption: figure.caption,
      imageType: figure.imageType || captionFigureType(figure.caption),
      sourceScore: figure.sourceScore,
      relevanceScore: Math.max(Number(figure.sourceScore || 0), Number(figure.referenceScore || 0)),
      referenceScore: Number(figure.referenceScore || 0),
      referenceEligible: Boolean(figure.referenceEligible),
      referenceAssessed: true,
      visionType: figure.visionType || 'unknown',
      visionConfidence: Number(figure.visionConfidence || 0),
      detectedText: figure.detectedText || '',
      textRisk: Boolean(figure.textRisk),
      referenceWarnings: figure.referenceWarnings || [],
      sourceWarnings: [
        ...(figure.caption ? [] : ['No nearby figure caption was detected']),
        ...(!figure.referenceEligible && ['diagram', 'chart-table', 'interface'].includes(figure.imageType)
          ? ['Kept as a direct source candidate; scientific diagrams and tables are not AI-edited']
          : []),
      ],
      width: figure.width,
      height: figure.height,
      aiGenerated: false,
    });
  }
  return {
    content: cleanExtractedPdfText(pages.join('\n\n')),
    contentMode: 'fulltext',
    contentSource: 'form-pdf-upload',
    sourceFigures,
    pageCount,
  };
}

async function extractPdfPackageCached(buffer, paperId = 'paper') {
  await ensureAssetsDir();
  const digest = crypto.createHash('sha256').update(buffer).digest('hex');
  const cacheFile = path.join(assetsDir, `.pdf-extraction-v2-${digest}.json`);
  try {
    const cached = JSON.parse(await fs.readFile(cacheFile, 'utf8'));
    const figures = Array.isArray(cached?.sourceFigures) ? cached.sourceFigures : [];
    const filesPresent = (await Promise.all(figures.map(async (figure) => {
      const fileName = path.basename(String(figure?.fileName || ''));
      if (!fileName) return false;
      try { await fs.access(path.join(assetsDir, fileName)); return true; } catch { return false; }
    }))).every(Boolean);
    if (cached?.content && filesPresent) {
      return { ...cached, extractionCache: 'hit' };
    }
  } catch {}
  const extracted = await extractPdfPackage(buffer, paperId);
  await fs.writeFile(cacheFile, JSON.stringify(extracted), 'utf8');
  return { ...extracted, extractionCache: 'miss' };
}
function makeComfyWorkflow({ prompt, negativePrompt, width, height, seed, filenamePrefix }) {
  return {
    '1': {
      class_type: 'CheckpointLoaderSimple',
      inputs: { ckpt_name: comfyuiCheckpoint },
    },
    '2': {
      class_type: 'CLIPTextEncode',
      inputs: { text: prompt, clip: ['1', 1] },
    },
    '3': {
      class_type: 'CLIPTextEncode',
      inputs: { text: negativePrompt, clip: ['1', 1] },
    },
    '4': {
      class_type: 'EmptyLatentImage',
      inputs: { width, height, batch_size: 1 },
    },
    '5': {
      class_type: 'KSampler',
      inputs: {
        seed: Math.max(0, Math.min(Number(seed) || 0, 2147483647)),
        steps: comfyuiSteps,
        cfg: comfyuiCfg,
        sampler_name: 'dpmpp_2m_sde',
        scheduler: 'karras',
        denoise: 1,
        model: ['1', 0],
        positive: ['2', 0],
        negative: ['3', 0],
        latent_image: ['4', 0],
      },
    },
    '6': {
      class_type: 'VAEDecode',
      inputs: { samples: ['5', 0], vae: ['1', 2] },
    },
    '7': {
      class_type: 'SaveImage',
      inputs: { filename_prefix: filenamePrefix, images: ['6', 0] },
    },
  };
}

function makeIpAdapterWorkflow({ prompt, negativePrompt, width, height, seed, filenamePrefix, referenceImage, referenceStrength }) {
  return {
    '1': {
      class_type: 'CheckpointLoaderSimple',
      inputs: { ckpt_name: comfyuiCheckpoint },
    },
    '2': {
      class_type: 'CLIPTextEncode',
      inputs: { text: prompt, clip: ['1', 1] },
    },
    '3': {
      class_type: 'CLIPTextEncode',
      inputs: { text: negativePrompt, clip: ['1', 1] },
    },
    '4': {
      class_type: 'LoadImage',
      inputs: { image: referenceImage },
    },
    '5': {
      class_type: 'IPAdapterUnifiedLoader',
      inputs: { model: ['1', 0], preset: ipAdapterPreset },
    },
    '6': {
      class_type: 'IPAdapter',
      inputs: {
        model: ['5', 0],
        ipadapter: ['5', 1],
        image: ['4', 0],
        weight: referenceStrength,
        start_at: 0,
        end_at: 0.82,
        weight_type: 'prompt is more important',
      },
    },
    '7': {
      class_type: 'EmptyLatentImage',
      inputs: { width, height, batch_size: 1 },
    },
    '8': {
      class_type: 'KSampler',
      inputs: {
        seed: Math.max(0, Math.min(Number(seed) || 0, 2147483647)),
        steps: comfyuiSteps,
        cfg: comfyuiCfg,
        sampler_name: 'dpmpp_2m_sde',
        scheduler: 'karras',
        denoise: 1,
        model: ['6', 0],
        positive: ['2', 0],
        negative: ['3', 0],
        latent_image: ['7', 0],
      },
    },
    '9': {
      class_type: 'VAEDecode',
      inputs: { samples: ['8', 0], vae: ['1', 2] },
    },
    '10': {
      class_type: 'SaveImage',
      inputs: { filename_prefix: filenamePrefix, images: ['9', 0] },
    },
  };
}

async function uploadComfyReference(buffer, fileName) {
  const form = new FormData();
  form.append('image', new Blob([buffer]), fileName);
  form.append('type', 'input');
  form.append('overwrite', 'true');
  const response = await fetchWithTimeout(`${comfyuiBaseUrl}/upload/image`, {
    method: 'POST',
    body: form,
  }, 60000);
  const result = await readJsonResponse(response, 'ComfyUI /upload/image');
  if (!result.name) throw new Error('ComfyUI did not return an uploaded image name');
  return result.subfolder ? `${result.subfolder}/${result.name}` : result.name;
}

async function queueComfyPrompt(workflow) {
  let response;
  try {
    response = await fetchWithTimeout(`${comfyuiBaseUrl}/prompt`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        prompt: workflow,
        client_id: crypto.randomUUID(),
      }),
    });
  } catch (error) {
    throw new Error(`ComfyUI is unavailable at ${comfyuiBaseUrl}: ${error.message}`);
  }
  const result = await readJsonResponse(response, 'ComfyUI /prompt');
  if (!result.prompt_id) {
    throw new Error(`ComfyUI did not return prompt_id: ${JSON.stringify(result).slice(0, 500)}`);
  }
  return result.prompt_id;
}

async function waitForComfyImage(promptId) {
  const deadline = Date.now() + comfyuiTimeoutMs;
  while (Date.now() < deadline) {
    const response = await fetchWithTimeout(`${comfyuiBaseUrl}/history/${encodeURIComponent(promptId)}`, {}, 30000);
    const history = await readJsonResponse(response, 'ComfyUI /history');
    const entry = history[promptId];
    if (entry?.status?.status_str === 'error') {
      throw new Error(`ComfyUI generation failed: ${JSON.stringify(entry.status.messages || []).slice(0, 1000)}`);
    }
    const outputs = entry?.outputs || {};
    for (const output of Object.values(outputs)) {
      const image = output?.images?.[0];
      if (image?.filename) return image;
    }
    await sleep(1500);
  }
  throw new Error(`ComfyUI generation timed out after ${Math.round(comfyuiTimeoutMs / 1000)} seconds`);
}

async function downloadComfyImage(image) {
  const params = new URLSearchParams({
    filename: image.filename,
    subfolder: image.subfolder || '',
    type: image.type || 'output',
  });
  const response = await fetchWithTimeout(`${comfyuiBaseUrl}/view?${params}`, {}, 60000);
  if (!response.ok) {
    throw new Error(`ComfyUI /view returned ${response.status}`);
  }
  return Buffer.from(await response.arrayBuffer());
}

function findImagePayload(value, seen = new Set()) {
  if (!value || typeof value !== 'object' || seen.has(value)) return null;
  seen.add(value);
  if (typeof value.url === 'string' && /^https?:\/\//i.test(value.url)) return { url: value.url };
  if (typeof value.image === 'string') {
    if (/^https?:\/\//i.test(value.image)) return { url: value.image };
    if (/^data:image\//i.test(value.image)) return { dataUrl: value.image };
  }
  if (typeof value.b64_json === 'string') return { base64: value.b64_json };
  if (typeof value.data === 'string' && value.data.length > 500) return { base64: value.data };
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    const found = findImagePayload(child, seen);
    if (found) return found;
  }
  return null;
}

async function qwenImageRequest({ prompt, negativePrompt, width, height, seed, referenceDataUrl, referenceDataUrls, qwenApiKey: requestQwenApiKey, requireRequestQwenKey, imageModel }) {
  const effectiveQwenApiKey = String(requestQwenApiKey || (requireRequestQwenKey ? '' : qwenApiKey) || '').trim();
  if (!effectiveQwenApiKey) throw new Error('QWEN_IMAGE_API_KEY_MISSING: configure a user Qwen key or QWEN_IMAGE_API_KEY');
  const content = [];
  const references = [
    ...(Array.isArray(referenceDataUrls) ? referenceDataUrls : []),
    referenceDataUrl,
  ].filter((value, index, values) => /^data:image\//i.test(String(value || '')) && values.indexOf(value) === index)
    .slice(0, maxQwenReferenceImages);
  for (const image of references) content.push({ image });
  content.push({ text: prompt });
  const response = await fetchWithTimeout(qwenApiUrl, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${effectiveQwenApiKey}`,
      'content-type': 'application/json',
      'x-dashscope-async': 'disable',
    },
    body: JSON.stringify({
      model: String(imageModel || qwenImageModel).trim(),
      input: { messages: [{ role: 'user', content }] },
      parameters: {
        negative_prompt: String(negativePrompt || '').slice(0, 500),
        prompt_extend: false,
        watermark: false,
        n: 1,
        size: `${width}*${height}`,
        seed: Math.max(0, Math.min(Number(seed) || 0, 2147483647)),
      },
    }),
  }, 180000);
  const payload = await readJsonResponse(response, 'Qwen Image API');
  const image = findImagePayload(payload);
  if (!image) throw new Error(`Qwen Image API returned no image: ${JSON.stringify(payload).slice(0, 700)}`);
  let buffer;
  if (image.dataUrl) buffer = Buffer.from(image.dataUrl.split(',')[1], 'base64');
  else if (image.base64) buffer = Buffer.from(image.base64, 'base64');
  else {
    const download = await fetchWithTimeout(image.url, {}, 90000);
    if (!download.ok) throw new Error(`Qwen image download returned ${download.status}`);
    buffer = Buffer.from(await download.arrayBuffer());
  }
  return {
    buffer,
    provider: 'qwen-image',
    requestId: payload.request_id || payload.requestId || '',
    providerRequest: {
      endpoint: qwenApiUrl,
      model: String(imageModel || qwenImageModel).trim(),
      size: `${width}*${height}`,
      prompt,
      negativePrompt: String(negativePrompt || '').slice(0, 500),
      referenceImageCount: references.length,
    },
  };
}

async function openAiCompatibleImageRequest({ prompt, negativePrompt, width, height, imageApiKey, imageModel, provider }) {
  const key = String(imageApiKey || '').trim();
  if (!key) throw new Error(`${provider.toUpperCase()}_IMAGE_API_KEY_MISSING: configure your image provider key`);
  const isOpenAi = provider === 'openai-image';
  const endpoint = isOpenAi
    ? 'https://api.openai.com/v1/images/generations'
    : 'https://ark.cn-beijing.volces.com/api/v3/images/generations';
  const requestedSize = isOpenAi ? '1536x1024' : doubaoImageSize(width, height);
  const safePrompt = `${prompt}. Avoid: ${String(negativePrompt || '').slice(0, 500)}`.slice(0, 4000);
  const requestBody = {
    model: String(imageModel || (isOpenAi ? 'gpt-image-1.5' : 'doubao-seedream-4-5-251128')).trim(),
    prompt: safePrompt,
    n: 1,
    size: requestedSize,
  };
  if (!isOpenAi) {
    requestBody.response_format = 'url';
    requestBody.watermark = false;
  }
  const response = await fetchWithTimeout(endpoint, {
    method: 'POST',
    headers: { authorization: `Bearer ${key}`, 'content-type': 'application/json' },
    body: JSON.stringify(requestBody),
  }, 180000);
  const payload = await readJsonResponse(response, isOpenAi ? 'OpenAI Image API' : 'Doubao Image API');
  const image = findImagePayload(payload);
  if (!image) throw new Error(`${provider} returned no image: ${JSON.stringify(payload).slice(0, 700)}`);
  let buffer;
  if (image.dataUrl) buffer = Buffer.from(image.dataUrl.split(',')[1], 'base64');
  else if (image.base64) buffer = Buffer.from(image.base64, 'base64');
  else {
    const download = await fetchWithTimeout(image.url, {}, 90000);
    if (!download.ok) throw new Error(`${provider} image download returned ${download.status}`);
    buffer = Buffer.from(await download.arrayBuffer());
  }
  return {
    buffer,
    provider,
    requestId: payload.id || payload.request_id || payload.requestId || '',
    providerRequest: {
      endpoint,
      model: requestBody.model,
      size: requestBody.size,
      prompt: requestBody.prompt,
      negativePrompt: String(negativePrompt || ''),
    },
  };
}

function doubaoImageSize(width, height) {
  const requestedWidth = Math.max(1, Number(width) || 900);
  const requestedHeight = Math.max(1, Number(height) || 600);
  const ratio = requestedWidth / requestedHeight;

  // Seedream requires at least 1024 px per side and roughly 3.7 MP in total.
  // Generate a provider-valid master image, then resize it to the requested output.
  if (ratio >= 1.35 && ratio <= 1.65) return '2496x1664';
  if (ratio >= 0.70 && ratio <= 0.82) return '1728x2304';
  if (ratio >= 0.90 && ratio <= 1.10) return '2048x2048';

  const minimumPixels = 3_686_400;
  const scale = Math.max(
    1024 / requestedWidth,
    1024 / requestedHeight,
    Math.sqrt(minimumPixels / (requestedWidth * requestedHeight)),
  );
  const scaledWidth = Math.min(4096, Math.ceil((requestedWidth * scale) / 64) * 64);
  const scaledHeight = Math.min(4096, Math.ceil((requestedHeight * scale) / 64) * 64);
  return `${scaledWidth}x${scaledHeight}`;
}

function requestedImageProvider(body = {}) {
  const provider = String(body.providerOverride || body.provider || body.imageProvider || imageProvider).trim().toLowerCase();
  return ['qwen-image', 'openai-image', 'doubao-image', 'local-comfyui'].includes(provider) ? provider : imageProvider;
}

async function generateImageFromProvider(options) {
  const provider = requestedImageProvider(options);
  if (provider === 'qwen-image') return qwenImageRequest(options);
  if (provider === 'openai-image' || provider === 'doubao-image') return openAiCompatibleImageRequest(options);
  if (provider !== 'local-comfyui') {
    throw new Error(`Unsupported IMAGE_PROVIDER: ${provider}.`);
  }
  const promptId = await queueComfyPrompt(options.workflow);
  const comfyImage = await waitForComfyImage(promptId);
  const buffer = await downloadComfyImage(comfyImage);
  return { buffer, promptId, provider: 'local-comfyui' };
}
function randomSeed() {
  return Number(BigInt(`0x${crypto.randomBytes(6).toString('hex')}`) % 9007199254740991n);
}

function shortLabel(value, fallback, maxLength = 28) {
  const text = String(value || fallback).trim().replace(/\s+/g, ' ');
  return text.length > maxLength ? `${text.slice(0, maxLength - 1).trim()}...` : text;
}

function renderAlgorithmSummarySvg(body) {
  const summary = body.methodSummary || {};
  const domainLabel = shortLabel(summary.domainLabel, 'Research method', 34);
  const explicitInputs = uniqueStrings(Array.isArray(summary.inputs) ? summary.inputs : [], 3);
  const groundedInputs = explicitInputs.length ? explicitInputs : uniqueStrings(body.uniqueAnchors || [], 3);
  const inputs = (groundedInputs.length ? groundedInputs : [domainLabel])
    .map((value) => shortLabel(value, domainLabel, 18));
  const fusionLabel = shortLabel(summary.fusionLabel, 'Method', 22);
  const outcomeLabel = shortLabel(summary.outcomeLabel, 'Outcome', 22);
  const benefitLabel = shortLabel(summary.benefitLabel, 'Evidence-based result', 34);
  const inputStartY = 300 - ((inputs.length - 1) * 32);
  const rows = inputs.map((label, index) => {
    const y = inputStartY + index * 64;
    const colors = ['#4f7d89', '#6f8f68', '#8a7199'];
    return [
      `<circle cx="148" cy="${y}" r="12" fill="${colors[index]}"/>`,
      `<text x="176" y="${y + 6}" font-size="17" font-weight="650" fill="#35414a" font-family="Noto Sans, Segoe UI, Arial, sans-serif">${esc(label)}</text>`,
      `<path d="M310 ${y} C365 ${y}, 372 300, 425 300" fill="none" stroke="#c9d1d6" stroke-width="2.5"/>`,
    ].join('');
  }).join('');
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600">
  <rect width="900" height="600" fill="#f7f9fa"/>
  <text x="72" y="92" font-size="30" font-weight="720" fill="#273139" font-family="Noto Sans, Segoe UI, Arial, sans-serif">${esc(domainLabel)}</text>
  <text x="72" y="126" font-size="16" fill="#69747c" font-family="Noto Sans, Segoe UI, Arial, sans-serif">${esc(benefitLabel)}</text>
  <line x1="72" y1="154" x2="828" y2="154" stroke="#d8dee2"/>
  ${rows}
  <rect x="425" y="220" width="190" height="160" rx="8" fill="#eaf0f1" stroke="#b9c7cb" stroke-width="2"/>
  <circle cx="520" cy="274" r="25" fill="#ffffff" stroke="#5f8791" stroke-width="3"/>
  <path d="M506 274 L517 285 L537 263" fill="none" stroke="#5e8169" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="520" y="338" text-anchor="middle" font-size="19" font-weight="700" fill="#303b42" font-family="Noto Sans, Segoe UI, Arial, sans-serif">${esc(fusionLabel)}</text>
  <path d="M615 300 L681 300" fill="none" stroke="#aebbc0" stroke-width="3"/>
  <path d="M681 300 L669 293 L669 307 Z" fill="#aebbc0"/>
  <rect x="692" y="220" width="136" height="160" rx="8" fill="#ffffff" stroke="#cbd4d8" stroke-width="2"/>
  <circle cx="760" cy="274" r="30" fill="none" stroke="#d8e0e3" stroke-width="8"/>
  <path d="M739 295 A30 30 0 0 1 781 253" fill="none" stroke="#75956f" stroke-width="8" stroke-linecap="round"/>
  <text x="760" y="338" text-anchor="middle" font-size="17" font-weight="700" fill="#35414a" font-family="Noto Sans, Segoe UI, Arial, sans-serif">${esc(outcomeLabel)}</text>
</svg>`;
}
function renderEditorialSatoriSvg(body, variant = 'academic') {
  const mode = body.visualMode || body.visualStrategy || (variant === 'method' ? 'algorithm-computational' : 'academic-card');
  const theme = academicCardTheme(mode);
  const summary = body.methodSummary || {};
  const brief = body.visualBrief && typeof body.visualBrief === 'object' ? body.visualBrief : {};
  const title = safeCoverHeadline(body.coverHeadline, body.title);
  const titleLines = wrapText(title, 14, 3);
  const titleSvg = renderTextLines(titleLines, 82, 250, {
    fontSize: 66,
    lineHeight: 84,
    fill: '#20282e',
    weight: 760,
    family: "'Noto Sans CJK SC', 'Microsoft YaHei', 'Noto Sans', sans-serif",
  });
  const methodInputs = uniqueStrings(Array.isArray(summary.inputs) ? summary.inputs : body.uniqueAnchors || [], 3);
  const academicAnchors = uniqueCardTerms([
    Array.isArray(body.visualAnchors) ? body.visualAnchors : [],
    Array.isArray(body.uniqueAnchors) ? body.uniqueAnchors : [],
    brief.technology,
    brief.subject,
    body.title,
  ], 3);
  const items = variant === 'method'
    ? [
      methodInputs.length ? methodInputs.join(' · ') : shortLabel(summary.domainLabel, 'Research input', 28),
      shortLabel(summary.fusionLabel, 'Core method', 52),
      shortLabel(summary.outcomeLabel, 'Research outcome', 48),
    ]
    : academicAnchors;
  while (items.length < 3) items.push(['Research object', 'Study context', 'Key contribution'][items.length]);
  const captions = variant === 'method'
    ? ['INPUT', 'METHOD', 'OUTCOME']
    : ['RESEARCH OBJECT', 'STUDY CONTEXT', 'CORE IDEA'];
  const rowSvg = items.slice(0, 3).map((item, index) => {
    const y = 676 + index * 216;
    const color = index === 1 ? theme.accent2 : theme.accent;
    const itemLines = wrapText(shortLabel(item, captions[index], 72), 32, 2);
    const itemSvg = renderTextLines(itemLines, 276, y, {
      fontSize: 40,
      lineHeight: 52,
      fill: '#263139',
      weight: 720,
      family: "'Noto Sans CJK SC', 'Microsoft YaHei', 'Noto Sans', sans-serif",
    });
    return `<g>
      <text x="82" y="${y - 34}" font-size="21" font-weight="760" fill="#7c878d" font-family="Noto Sans, sans-serif">${captions[index]}</text>
      <text x="82" y="${y + 10}" font-size="27" font-weight="820" fill="${color}" font-family="Noto Sans, sans-serif">0${index + 1}</text>
      <line x1="166" y1="${y}" x2="230" y2="${y}" stroke="${color}" stroke-width="9" stroke-linecap="round"/>
      ${itemSvg}
      <line x1="82" y1="${y + 112}" x2="1160" y2="${y + 112}" stroke="#d8dedb" stroke-width="2"/>
    </g>`;
  }).join('');
  const evidenceText = variant === 'method'
    ? shortLabel(summary.benefitLabel, 'Evidence-grounded method summary', 86)
    : shortLabel(brief.activity || brief.setting || brief.subject, 'Evidence-grounded publication visual', 86);
  const evidenceSvg = renderTextLines(wrapText(evidenceText, 34, 3), 82, 1410, {
    fontSize: 29,
    lineHeight: 42,
    fill: '#4e5a61',
    weight: 560,
    family: "'Noto Sans CJK SC', 'Microsoft YaHei', 'Noto Sans', sans-serif",
  });
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1242" height="1660" viewBox="0 0 1242 1660">
  <rect width="1242" height="1660" fill="#f5f7f5"/>
  <rect x="82" y="86" width="78" height="8" fill="${theme.accent}"/>
  <text x="82" y="142" font-size="27" font-weight="760" fill="${theme.accent}" font-family="Noto Sans CJK SC, Microsoft YaHei, Noto Sans, sans-serif">${esc(variant === 'method' ? 'METHOD SUMMARY' : theme.label)}</text>
  <text x="1160" y="142" text-anchor="end" font-size="25" fill="#7a858c" font-family="Noto Sans CJK SC, Microsoft YaHei, Noto Sans, sans-serif">${esc(shortLabel(body.venue || body.strategyLabel || mode, 'Academic publication', 42))}</text>
  ${titleSvg}
  <line x1="82" y1="520" x2="1160" y2="520" stroke="#d8dedb" stroke-width="2"/>
  ${rowSvg}
  <text x="82" y="1350" font-size="20" font-weight="800" fill="${theme.accent}" font-family="Noto Sans, sans-serif">EVIDENCE NOTE</text>
  ${evidenceSvg}
  <text x="82" y="1550" font-size="25" fill="#707c82" font-family="Noto Sans CJK SC, Microsoft YaHei, Noto Sans, sans-serif">基于论文原文整理 · ${variant === 'method' ? '方法摘要图' : '视觉摘要'}</text>
</svg>`;
}
async function renderMethodSummaryCandidate(body) {
  const articleSlug = safeSlug(body.articleSlug || body.title, 'publication');
  const svg = renderEditorialSatoriSvg(body, 'method');
  const baseName = `${articleSlug}-method-summary-${stableId({
    methodSummary: body.methodSummary,
    visualMode: body.visualMode,
  })}`;
  const svgFileName = `${baseName}.svg`;
  const fileName = `${baseName}.jpg`;
  await ensureAssetsDir();
  await fs.writeFile(path.join(assetsDir, svgFileName), svg, 'utf8');
  const rawOutput = await sharp(Buffer.from(svg))
    .resize(XHS_COVER_SIZE.width, XHS_COVER_SIZE.height)
    .jpeg({ quality: 92, mozjpeg: true })
    .toBuffer();
  const rawFileName = `${baseName}-raw.jpg`;
  await fs.writeFile(path.join(assetsDir, rawFileName), rawOutput);

  await fs.writeFile(path.join(assetsDir, fileName), rawOutput);

  return {
    type: 'method-summary',
    title: body.title || 'Publication method summary',
    fileName,
    url: `${assetsPublicUrl}/${fileName}`,
    rawUrl: `${assetsPublicUrl}/${rawFileName}`,
    svgUrl: `${assetsPublicUrl}/${svgFileName}`,
    alt: `Simplified method summary for ${body.title || 'the publication'}`,
    suggestedPlacement: body.outputProfile === 'xiaohongshu-cover' ? 'Use as a Xiaohongshu cover' : 'Use as the publication list thumbnail',
    aiGenerated: false,
    provider: 'renderer-api',
    model: 'deterministic-svg-v2-editorial',
    seed: null,
    prompt: '',
    qualityScore: 1,
    qualityWarnings: [],
    attempts: 1,
  };
}

async function perceptualHash(buffer) {
  const pixels = await sharp(buffer).resize(9, 8, { fit: 'fill' }).greyscale().raw().toBuffer();
  let bits = '';
  for (let y = 0; y < 8; y += 1) {
    for (let x = 0; x < 8; x += 1) {
      const offset = y * 9 + x;
      bits += pixels[offset] > pixels[offset + 1] ? '1' : '0';
    }
  }
  return bits.match(/.{1,4}/g).map((chunk) => Number.parseInt(chunk, 2).toString(16)).join('');
}

async function colorSignature(buffer) {
  const { data, info } = await sharp(buffer)
    .resize(4, 4, { fit: 'fill' })
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const signature = [];
  for (let index = 0; index < info.width * info.height * info.channels; index += info.channels) {
    signature.push(
      Number((data[index] / 255).toFixed(3)),
      Number((data[index + 1] / 255).toFixed(3)),
      Number((data[index + 2] / 255).toFixed(3)),
    );
  }
  return signature;
}

async function getOcrWorker() {
  if (!ocrEnabled) return null;
  if (!ocrWorkerPromise) {
    ocrWorkerPromise = import('tesseract.js')
      .then(async ({ createWorker }) => createWorker('eng', 1, { cachePath: ocrCacheDir }))
      .catch((error) => {
        ocrWorkerPromise = null;
        throw error;
      });
  }
  return ocrWorkerPromise;
}

async function assessTextRisk(buffer) {
  if (!ocrEnabled) return { textRisk: false, textReview: false, ocrConfidence: null, detectedText: '', ocrAvailable: false };
  try {
    const worker = await getOcrWorker();
    const prepared = await sharp(buffer).resize({ width: 1800, withoutEnlargement: false }).greyscale().normalize().sharpen({ sigma: 1.1 }).threshold(155).png().toBuffer();
    const result = await Promise.race([
      worker.recognize(prepared),
      new Promise((_, reject) => setTimeout(() => reject(new Error('OCR timed out')), 45000)),
    ]);
    const detectedText = normalizeWhitespace(result?.data?.text || '').slice(0, 160);
    const confidence = Number(result?.data?.confidence || 0);
    const compactLength = detectedText.replace(/[^a-z0-9\u4e00-\u9fff]/gi, '').length;
    const tokens = detectedText.match(/[a-z0-9\u4e00-\u9fff]{2,}/gi) || [];
    const wordItems = Array.isArray(result?.data?.words) ? result.data.words : [];
    const confidentWords = wordItems.filter((word) => (
      Number(word?.confidence || 0) >= 55
      && /[a-z0-9\u4e00-\u9fff]{2,}/i.test(String(word?.text || ''))
    ));
    const textRisk = confidence >= 42
      && compactLength >= 6
      && (confidentWords.length >= 2 || tokens.filter((token) => token.length >= 3).length >= 2);
    return {
      textRisk,
      textReview: !textRisk && confidence >= 20 && compactLength >= 18,
      ocrConfidence: Number(confidence.toFixed(1)),
      detectedText,
      ocrAvailable: true,
    };
  } catch (error) {
    return { textRisk: false, textReview: false, ocrConfidence: null, detectedText: '', ocrAvailable: false, ocrError: error.message };
  }
}

function compactSemanticSignature(values) {
  const source = Array.from(values || []);
  if (!source.length) return [];
  const bins = 64;
  const output = [];
  for (let bin = 0; bin < bins; bin += 1) {
    const start = Math.floor((bin * source.length) / bins);
    const end = Math.max(start + 1, Math.floor(((bin + 1) * source.length) / bins));
    const slice = source.slice(start, end);
    output.push(Number((slice.reduce((sum, value) => sum + Number(value || 0), 0) / slice.length).toFixed(5)));
  }
  return output;
}

async function getVisionPipelines() {
  if (!localVisionEnabled) return null;
  if (!visionPipelinesPromise) {
    visionPipelinesPromise = import('@huggingface/transformers').then(async ({ pipeline, env, RawImage }) => {
      env.cacheDir = modelCacheDir;
      const classifier = await pipeline('zero-shot-image-classification', visionModel);
      const featureExtractor = await pipeline('image-feature-extraction', visionModel);
      return { classifier, featureExtractor, RawImage };
    }).catch((error) => {
      visionPipelinesPromise = null;
      throw error;
    });
  }
  return visionPipelinesPromise;
}

async function readReferenceBuffer(reference) {
  const dataUrl = String(reference?.dataUrl || '').trim();
  if (/^data:image\/(?:jpeg|jpg|png|webp);base64,/i.test(dataUrl)) {
    const encoded = dataUrl.slice(dataUrl.indexOf(',') + 1);
    if (encoded.length > 28 * 1024 * 1024) throw new Error('Embedded reference image exceeds 20 MB');
    return Buffer.from(encoded, 'base64');
  }
  const fileName = String(reference?.fileName || '').trim();
  if (fileName) {
    const localPath = path.join(assetsDir, path.basename(fileName));
    try { return await fs.readFile(localPath); } catch {}
  }
  const url = String(reference?.url || reference?.imageUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return null;
  if (url.startsWith(`${assetsPublicUrl}/`)) {
    const localName = path.basename(new URL(url).pathname);
    try { return await fs.readFile(path.join(assetsDir, localName)); } catch {}
  }
  const response = await fetchWithTimeout(url, {}, 45000);
  if (!response.ok) throw new Error(`Reference image returned ${response.status}`);
  const length = Number(response.headers.get('content-length') || 0);
  if (length > 20 * 1024 * 1024) throw new Error('Reference image exceeds 20 MB');
  return Buffer.from(await response.arrayBuffer());
}

async function classifyReferenceImage(buffer, reference = {}) {
  const metadata = await sharp(buffer).metadata();
  const width = Number(metadata.width || reference.width || 0);
  const height = Number(metadata.height || reference.height || 0);
  const captionType = captionFigureType(reference.caption || '');
  let visionType = 'unknown';
  let visionConfidence = 0;
  try {
    const tools = await getVisionPipelines();
    const { data, info } = await sharp(buffer).resize(224, 224, { fit: 'cover' }).removeAlpha().raw().toBuffer({ resolveWithObject: true });
    const image = new tools.RawImage(new Uint8ClampedArray(data), info.width, info.height, info.channels);
    const labels = [
      'a real research or field study photograph',
      'a photograph of a physical device, artifact, building, or prototype',
      'a software interface screenshot',
      'a scientific diagram or workflow',
      'a chart, table, plot, or equation',
      'a logo, icon, or decorative graphic',
    ];
    const classified = await tools.classifier(image, labels);
    const winner = classified[0] || {};
    visionConfidence = Number(Number(winner.score || 0).toFixed(2));
    const index = labels.indexOf(winner.label);
    visionType = ['research-photo', 'artifact-device', 'interface', 'diagram', 'chart-table', 'logo-decoration'][index] || 'unknown';
  } catch {}
  const text = await assessTextRisk(buffer);
  const detectedCharacters = String(text.detectedText || '').replace(/[^a-z0-9\u4e00-\u9fff]/gi, '').length;
  let imageType = captionType !== 'unknown' ? captionType : visionType;
  if (detectedCharacters >= 55 && !['research-photo', 'artifact-device'].includes(captionType)) {
    imageType = imageType === 'interface' ? 'interface' : 'chart-table';
  }
  const areaScore = Math.min(1, (width * height) / (900 * 600));
  const aspect = height ? width / height : 0;
  const aspectScore = aspect >= 1.15 && aspect <= 2.2 ? 1 : aspect >= 0.75 && aspect <= 2.8 ? 0.68 : 0.35;
  const sourceScore = Number(reference.relevanceScore || reference.sourceScore || 0.5);
  const typeBonus = ['research-photo', 'artifact-device'].includes(imageType) ? 0.18 : imageType === 'interface' ? 0.02 : imageType === 'unknown' ? 0 : -0.24;
  const textPenalty = text.textRisk ? 0.28 : text.textReview ? 0.08 : 0;
  const referenceScore = Number(Math.max(0, Math.min(1,
    0.42 * sourceScore + 0.24 * areaScore + 0.16 * aspectScore + typeBonus - textPenalty,
  )).toFixed(2));
  const visualTypeConfirmed = captionType !== 'unknown' || visionConfidence >= 0.62;
  const referenceTypeEligible = ['research-photo', 'artifact-device'].includes(imageType)
    || (imageType === 'interface' && detectedCharacters < 18);
  const minimumReferenceScore = imageType === 'interface' ? 0.58 : 0.55;
  const referenceEligible = referenceTypeEligible
    && visualTypeConfirmed
    && width >= 360 && height >= 220 && referenceScore >= minimumReferenceScore && !text.textRisk;
  return {
    imageType,
    referenceScore,
    referenceEligible,
    width,
    height,
    visionType,
    visionConfidence,
    detectedText: text.detectedText || '',
    textRisk: Boolean(text.textRisk),
    referenceWarnings: [
      ...(!referenceEligible ? ['Not suitable for reference-conditioned generation'] : []),
      ...(text.textRisk ? ['Reference contains substantial text'] : []),
    ],
  };
}

async function prepareReferenceContext(body) {
  const provider = requestedImageProvider(body);
  const referenceMode = String(body.referenceMode || 'auto').toLowerCase();
  const raw = [
    ...(Array.isArray(body.referenceImages) ? body.referenceImages : []),
    ...(Array.isArray(body.sourceFigures) ? body.sourceFigures : []),
  ];
  if (body.sourceImage) raw.push({ type: 'source-original', url: body.sourceImage, caption: body.sourceImageAlt || '' });
  const seen = new Set();
  const assessed = [];
  for (const item of raw.slice(0, 8)) {
    const key = String(item?.fileName || item?.url || item?.id || '');
    if (!key || seen.has(key)) continue;
    seen.add(key);
    try {
      const buffer = await readReferenceBuffer(item);
      if (!buffer) continue;
      const classification = item.referenceAssessed
        ? {
          imageType: item.imageType || 'unknown',
          referenceScore: Number(item.referenceScore || 0),
          referenceEligible: Boolean(item.referenceEligible),
          width: Number(item.width || 0),
          height: Number(item.height || 0),
          visionType: item.visionType || 'unknown',
          visionConfidence: Number(item.visionConfidence || 0),
          detectedText: item.detectedText || '',
          textRisk: Boolean(item.textRisk),
          referenceWarnings: item.referenceWarnings || [],
        }
        : await classifyReferenceImage(buffer, item);
      assessed.push({ ...item, ...classification, _buffer: buffer });
    } catch (error) {
      assessed.push({ ...item, referenceEligible: false, referenceScore: 0, referenceWarnings: [error.message] });
    }
  }
  assessed.sort((a, b) => Number(b.referenceScore || 0) - Number(a.referenceScore || 0));
  const publicReferences = assessed.map(({ _buffer, dataUrl, ...item }) => item);
  if (referenceMode === 'off' || (!ipAdapterEnabled && provider === 'local-comfyui') || ['openai-image', 'doubao-image'].includes(provider)) {
    return { mode: referenceMode, selected: null, uploadedName: '', references: publicReferences };
  }
  const selectedItems = assessed.filter((item) => item.referenceEligible && item._buffer).slice(0, maxReferenceCandidates);
  const selected = selectedItems[0];
  if (!selectedItems.length) {
    if (referenceMode === 'required') throw new Error('REFERENCE_IMAGE_REQUIRED: no suitable research photo, device, artifact, or building image was found');
    return { mode: referenceMode, selected: null, uploadedName: '', references: publicReferences };
  }
  const normalizedReferences = [];
  for (const item of selectedItems) {
    const normalized = await sharp(item._buffer).rotate().resize(1200, 900, { fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 92 }).toBuffer();
    const { _buffer, dataUrl, ...publicItem } = item;
    publicItem.source = publicItem.fileName || publicItem.url || publicItem.id || `pdf-page-${publicItem.pageNumber || 'unknown'}`;
    normalizedReferences.push({ publicItem, normalized });
  }
  const primary = normalizedReferences[0];
  const uploadName = `${safeSlug(body.articleSlug || body.title, 'paper')}-reference-${stableId({
    url: primary.publicItem.url,
    fileName: primary.publicItem.fileName,
    pageNumber: primary.publicItem.pageNumber,
    id: primary.publicItem.id,
  })}.jpg`;
  const uploadedName = provider === 'local-comfyui'
    ? await uploadComfyReference(primary.normalized, uploadName)
    : '';
  const selectedDataUrls = normalizedReferences.map(({ normalized }) => `data:image/jpeg;base64,${normalized.toString('base64')}`);
  const selectedList = normalizedReferences.map(({ publicItem }) => publicItem);
  return {
    mode: referenceMode,
    selected: selectedList[0],
    selectedList,
    selectedDataUrl: selectedDataUrls[0],
    selectedDataUrls,
    uploadedName,
    references: publicReferences,
  };
}

async function assessSemanticRisk(buffer, body = {}) {
  if (!localVisionEnabled) {
    return { semanticScore: null, semanticSignature: [], anatomyRisk: 'unknown', handDetected: null, visionAvailable: false };
  }
  try {
    const tools = await getVisionPipelines();
    const { data, info } = await sharp(buffer).resize(224, 224, { fit: 'cover' }).removeAlpha().raw().toBuffer({ resolveWithObject: true });
    const image = new tools.RawImage(new Uint8ClampedArray(data), info.width, info.height, info.channels);
    const brief = body.visualBrief && typeof body.visualBrief === 'object' ? body.visualBrief : {};
    const positives = uniqueStrings([
      body.mustShow || [],
      brief.subject,
      brief.technology,
      brief.activity,
    ], 5).map((value) => `a photograph showing ${value}`);
    const negatives = uniqueStrings([
      body.mustAvoid || [],
      'an unrelated generic office stock photograph',
    ], 5).map((value) => `a photograph showing ${value}`);
    let semanticScore = null;
    let semanticLabels = [];
    if (positives.length) {
      const labels = [...positives, ...negatives];
      semanticLabels = await tools.classifier(image, labels);
      const positiveSet = new Set(positives);
      semanticScore = semanticLabels
        .filter((item) => positiveSet.has(item.label))
        .reduce((sum, item) => sum + Number(item.score || 0), 0);
      semanticScore = Number(Math.max(0, Math.min(1, semanticScore)).toFixed(2));
    }
    const anatomyLabels = [
      'a photograph with clearly visible human hands',
      'a photograph without visible human hands',
      'a scene without people',
      'a close-up of a human face',
    ];
    const anatomy = await tools.classifier(image, anatomyLabels);
    const handScore = Number(anatomy.find((item) => item.label === anatomyLabels[0])?.score || 0);
    const handDetected = handScore >= 0.38;
    const peoplePolicy = String(body.peoplePolicy || body.riskProfile?.peoplePolicy || 'optional');
    const avoidHands = Boolean(body.riskProfile?.avoidHands) || peoplePolicy === 'none';
    const anatomyRisk = handDetected
      ? peoplePolicy === 'required' ? 'review' : avoidHands ? 'reject' : 'review'
      : 'none';
    let semanticSignature = [];
    let visionFeatureError = '';
    try {
      const features = await tools.featureExtractor(image);
      semanticSignature = compactSemanticSignature(features?.data);
    } catch (error) {
      visionFeatureError = error.message;
    }
    return {
      semanticScore,
      semanticLabels: semanticLabels.slice(0, 6).map((item) => ({ label: item.label, score: Number(Number(item.score || 0).toFixed(3)) })),
      semanticSignature,
      anatomyRisk,
      handDetected,
      handScore: Number(handScore.toFixed(2)),
      visionAvailable: true,
      visionFeatureError,
    };
  } catch (error) {
    return {
      semanticScore: null,
      semanticSignature: [],
      anatomyRisk: 'unknown',
      handDetected: null,
      visionAvailable: false,
      visionError: error.message,
    };
  }
}

async function assessImageQuality(buffer, body = {}) {
  const metadata = await sharp(buffer).metadata();
  const stats = await sharp(buffer).stats();
  const channels = stats.channels.slice(0, 3);
  const mean = channels.reduce((sum, channel) => sum + Number(channel.mean || 0), 0) / Math.max(1, channels.length);
  const contrast = channels.reduce((sum, channel) => sum + Number(channel.stdev ?? channel.std ?? 0), 0) / Math.max(1, channels.length);
  const small = await sharp(buffer).resize(96, 64, { fit: 'fill' }).greyscale().raw().toBuffer();
  let adjacentDelta = 0;
  let comparisons = 0;
  for (let y = 0; y < 64; y += 1) {
    for (let x = 1; x < 96; x += 1) {
      const offset = y * 96 + x;
      adjacentDelta += Math.abs(small[offset] - small[offset - 1]);
      comparisons += 1;
    }
  }
  for (let y = 1; y < 64; y += 1) {
    for (let x = 0; x < 96; x += 1) {
      const offset = y * 96 + x;
      adjacentDelta += Math.abs(small[offset] - small[offset - 96]);
      comparisons += 1;
    }
  }
  const edgeScore = adjacentDelta / Math.max(1, comparisons);
  const warnings = [];
  if ((metadata.width || 0) < 800 || (metadata.height || 0) < 500) warnings.push({ code: 'small-image', severity: 'error', message: 'Image is smaller than the publication thumbnail target.' });
  if (mean < 34) warnings.push({ code: 'too-dark', severity: 'warn', message: 'Image is very dark.' });
  if (mean > 226) warnings.push({ code: 'too-bright', severity: 'warn', message: 'Image is very bright.' });
  if (contrast < 18) warnings.push({ code: 'low-contrast', severity: 'warn', message: 'Image has low tonal contrast.' });
  if (edgeScore < 4.2) warnings.push({ code: 'soft-or-flat', severity: 'warn', message: 'Image appears overly soft or visually flat.' });
  if (edgeScore > 52 && contrast > 54) warnings.push({ code: 'busy-detail-risk', severity: 'warn', message: 'Image may contain noisy or overly busy fine details.' });
  const qualityMode = String(body.qualityMode || 'warn').toLowerCase();
  const text = qualityMode === 'off' ? { textRisk: false, ocrAvailable: false } : await assessTextRisk(buffer);
  const semantic = qualityMode === 'off' ? { semanticScore: null, semanticSignature: [], anatomyRisk: 'unknown', visionAvailable: false } : await assessSemanticRisk(buffer, body);
  if (text.textRisk) warnings.push({ code: 'generated-text-risk', severity: 'error', message: `OCR detected possible generated text${text.detectedText ? `: ${text.detectedText}` : ''}.` });
  if (text.textReview) warnings.push({ code: 'possible-text-review', severity: 'info', message: 'Low-confidence OCR found text-like texture; review visually, but do not reject automatically.' });
  if (text.ocrError) warnings.push({ code: 'ocr-unavailable', severity: 'info', message: `OCR check unavailable: ${text.ocrError}` });
  if (semantic.semanticScore !== null && semantic.semanticScore < 0.34) warnings.push({ code: 'low-semantic-match', severity: 'warn', message: 'The image has a weak match to the required paper-specific visual anchors.' });
  if (semantic.anatomyRisk === 'reject') warnings.push({ code: 'unnecessary-hand-risk', severity: 'error', message: 'Visible hands were detected although the selected composition does not require them.' });
  if (semantic.anatomyRisk === 'review') warnings.push({ code: 'anatomy-review', severity: 'warn', message: 'A person or hand is part of this scene and needs manual anatomy review.' });
  if (semantic.visionError) warnings.push({ code: 'vision-check-unavailable', severity: 'info', message: `Semantic check unavailable: ${semantic.visionError}` });
  if (semantic.visionFeatureError) warnings.push({ code: 'vision-feature-unavailable', severity: 'info', message: `Image similarity feature unavailable: ${semantic.visionFeatureError}` });
  const penalty = warnings.reduce((sum, warning) => sum + (warning.severity === 'error' ? 0.3 : warning.severity === 'warn' ? 0.11 : 0), 0);
  const semanticAdjustment = semantic.semanticScore === null ? 0 : (semantic.semanticScore - 0.5) * 0.16;
  const score = Math.max(0, Math.min(1, Number((1 - penalty + semanticAdjustment).toFixed(2))));
  return {
    qualityScore: score,
    qualityWarnings: warnings,
    qualityMetrics: {
      width: metadata.width || 0,
      height: metadata.height || 0,
      brightness: Number(mean.toFixed(2)),
      contrast: Number(contrast.toFixed(2)),
      edgeScore: Number(edgeScore.toFixed(2)),
      ocrConfidence: text.ocrConfidence ?? null,
    },
    qualityPass: score >= 0.68 && !warnings.some((warning) => warning.severity === 'error'),
    textRisk: Boolean(text.textRisk),
    detectedText: text.detectedText || '',
    semanticScore: semantic.semanticScore,
    semanticLabels: semantic.semanticLabels || [],
    semanticSignature: semantic.semanticSignature || [],
    anatomyRisk: semantic.anatomyRisk || 'unknown',
    handDetected: semantic.handDetected ?? null,
    perceptualHash: await perceptualHash(buffer),
    colorSignature: await colorSignature(buffer),
  };
}
function sanitizeGeneratedPrompt(value) {
  const unsafeVisualLanguage = /\b(measurement lines?|mathematical curves?|equations?|axis labels?|legends?|annotated wireframe|wireframe representation|model overlay|split[- ]screen|split view|one half)\b/i;
  return String(value || '')
    .split(/(?<=[.!?])\s+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence && !unsafeVisualLanguage.test(sentence))
    .join(' ')
    .trim();
}

function interfaceIsCore(body = {}, prompt = '') {
  const cues = normalizeWhitespace([
    prompt,
    ...(Array.isArray(body.mustShow) ? body.mustShow : []),
    ...(Array.isArray(body.uniqueAnchors) ? body.uniqueAnchors : []),
    body.visualBrief?.subject,
    body.visualBrief?.technology,
  ].filter(Boolean).join(' '));
  return /\b(interface|screen|display|waveform|dashboard|canvas|software|mobile app|web app)\b/i.test(cues);
}

function hardPromptConflicts(prompt, body = {}) {
  const value = normalizeWhitespace(prompt);
  const conflicts = [];
  if (String(body.handsPolicy || '').toLowerCase() === 'avoid'
      && /\b(held|holding|grasping|touching|tapping|pinching|hands? visible)\b/i.test(value)) {
    conflicts.push('positive-hand-action-with-hands-disabled');
  }
  if (/\b(?:all|the) screens?[^.]{0,60}(?:blank|turned away)\b/i.test(value)
      && /\b(?:screen|interface|display)[^.]{0,90}(?:waveform|canvas|controls?|icons?|showing|displaying)\b/i.test(value)) {
    conflicts.push('visible-interface-versus-blank-screen');
  }
  return [...new Set(conflicts)];
}

function resolveHardPromptConflicts(prompt, body = {}) {
  const conflicts = hardPromptConflicts(prompt, body);
  let resolved = normalizeWhitespace(prompt);
  if (conflicts.includes('visible-interface-versus-blank-screen')) {
    resolved = resolved.replace(/All screens and surfaces are blank or turned away; no readable text, labels, logos, captions, or watermarks\.?/gi, 'Show only abstract non-readable interface shapes, waveforms, or unlabeled controls; keep every text area blank and show no logos or watermarks.');
  }
  if (conflicts.includes('positive-hand-action-with-hands-disabled')) resolved = resolved.replace(/\b(held|holding|grasping|touching|tapping|pinching|hands? visible)\b/gi, 'positioned');
  return { prompt: resolved, conflicts, remainingConflicts: hardPromptConflicts(resolved, body) };
}

function promptConflictResolution(prompt, body = {}, index = 0) {
  const originalPrompt = normalizeWhitespace(prompt);
  const handsRequired = body.handsRequired === true;
  const roles = Array.isArray(body.compositionRoles) ? body.compositionRoles : [];
  const screenIsCore = interfaceIsCore(body, originalPrompt);
  const role = roles[index] || (index === 0 ? 'artifact-closeup' : 'study-context');
  const conflicts = [];
  if (/\b(split[- ]?(?:screen|scene|view)|diptych|triptych|collage|on the left\b.*\bon the right|two[- ]panel)\b/i.test(originalPrompt)) {
    conflicts.push('multi-panel-layout');
  }
  if (/\b(screen (?:showing|displaying)[^.;,]{0,60}(?:text|prompt|words|labels)|(?:readable|visible)[^.;,]{0,30}(?:text|words|labels)|text prompt|interface text|captioned|title text)\b/i.test(originalPrompt)) {
    conflicts.push('generated-text-requirement');
  }

  const forbidden = normalizeWhitespace([
    body.negativePrompt,
    body.strategyNegativePrompt,
    ...(Array.isArray(body.mustAvoid) ? body.mustAvoid : []),
  ].filter(Boolean).join(' '));
  const contradictoryTerms = [
    ['wireframe-or-overlay', /\b(wireframe|grid overlay|model overlay|measurement lines?)\b/i],
    ['holographic-technology', /\b(hologram|holographic)\b/i],
    ['generic-laptop-or-screen', /\b(laptop|computer screen|monitor)\b/i],
  ];
  for (const [name, pattern] of contradictoryTerms) {
    if (pattern.test(originalPrompt) && pattern.test(forbidden)) conflicts.push(`positive-negative-conflict:${name}`);
  }
  const sanitized = sanitizeGeneratedPrompt(originalPrompt);
  if (!conflicts.length && sanitized.length >= 80) {
    return { prompt: sanitized, originalPrompt, conflicts, rebuilt: false };
  }
  const unsafeText = /\b(text|words|label|caption|title|logo|watermark|screen prompt|interface text)\b/i;
  const unsafeHands = /\b(hand|hands|handheld|holding|held|grasping|touching|tapping|pinching|finger|fingers|gesture)\b/i;
  const anchorPool = uniqueStrings([
    body.mustShow || [],
    body.uniqueAnchors || [],
    body.visualBrief?.subject,
    body.visualBrief?.technology,
    body.visualBrief?.activity,
  ], 12).filter((anchor) => !unsafeText.test(anchor));
  const anchors = anchorPool.length <= 2
    ? anchorPool
    : index === 0 ? anchorPool.slice(0, 2) : [anchorPool[1], anchorPool[2] || anchorPool[0]];
  const focus = anchors.length
    ? `The scene centers on only these paper-supported elements: ${anchors.join(', ')}`
    : `The scene centers on the concrete research subject described by ${String(body.title || 'the paper').slice(0, 120)}`;
  const roleOpening = {
    'artifact-closeup': 'Create one coherent close editorial view of the paper-supported research object or artifact.',
    'study-context': 'Create one coherent documentary editorial view of the paper-supported study environment.',
    'interaction-scene': 'Create one coherent documentary scene of the exact paper-supported interaction.',
    'editorial-concept': 'Create one coherent restrained editorial still life using concrete paper-supported objects.',
    'method-summary': 'Create one coherent method-oriented visual using no more than three concrete paper-supported elements.',
  }[role] || 'Create one coherent academic editorial image grounded in the paper.';
  const rebuiltPrompt = [
    roleOpening,
    `${focus}.`,
    compositionInstruction(role, body),
    'Use a single scene with one focal subject; never use split panels, a collage, or before-and-after framing.',
    'Hands are optional. When an evidenced interaction benefits from hands, show only one natural interaction with anatomically plausible hands and no extra limbs.',
    screenIsCore
      ? 'If a screen is central, show only abstract non-readable interface shapes, waveforms, or unlabeled controls; keep every text area blank and show no logos or watermarks.'
      : 'Keep incidental screens blank or turned away; no readable text, labels, logos, captions, or watermarks.',
  ].join(' ');
  return { prompt: rebuiltPrompt, originalPrompt, conflicts: conflicts.length ? conflicts : ['insufficient-specific-prompt'], rebuilt: true };
}

function concisePaperCues(body = {}, role = 'artifact-closeup') {
  const brief = body.visualBrief && typeof body.visualBrief === 'object' ? body.visualBrief : {};
  const unsafeAnchor = /\b(overlay|measurement|wireframe|curve|equation|label|text|caption|title|logo|watermark)\b/i;
  const unsafeHands = /\b(hand|hands|handheld|holding|held|grasping|touching|tapping|pinching|finger|fingers|gesture)\b/i;
  const handsRequired = body.handsRequired === true;
  const allAnchors = uniqueStrings([
    body.mustShow || [],
    body.uniqueAnchors || [],
    brief.subject,
    brief.technology,
    brief.activity,
  ], 12).filter((anchor) => !unsafeAnchor.test(anchor));
  const rolePattern = role === 'study-context'
    ? /\b(site|gate|building|architecture|environment|scanner|scanning|photogrammetry|uav|field|museum|garden)\b/i
    : role === 'interaction-scene'
      ? /\b(participant|visitor|user|interaction|wearable|headset|recorder|device)\b/i
      : /\b(artifact|device|material|model|dou-gong|bracket|joint|timber|object|instrument)\b/i;
  const anchors = uniqueStrings([
    allAnchors.filter((anchor) => rolePattern.test(anchor)),
    allAnchors,
  ], 3);
  return [
    anchors.length ? `show no more than these three paper-specific anchors: ${anchors.join(', ')}` : '',
    body.mustAvoid?.length ? `do not introduce unsupported elements: ${uniqueStrings(body.mustAvoid, 8).join(', ')}` : '',
  ].filter(Boolean);
}

function compositionInstruction(role, body = {}) {
  const peoplePolicy = String(body.peoplePolicy || body.riskProfile?.peoplePolicy || 'optional');
  const instructions = {
    'artifact-closeup': 'composition role: close view of the exact core artifact, device, material, heritage object, or study object; the object occupies most of the frame; do not use a generic laboratory scene',
    'study-context': 'composition role: wider documentary view of the exact research site or study environment; show the context without turning the image into a travel photograph',
    'interaction-scene': 'composition role: one participant performing the exact interaction supported by the paper; the device or research object remains clearly visible',
    'editorial-concept': 'composition role: restrained editorial still life using only concrete domain objects supported by the paper; no abstract glowing technology motifs',
    'method-summary': 'composition role: a simple method-oriented visual with three or fewer concrete elements; avoid people and fake experimental scenes',
  };
  const handsRequired = body.handsRequired === true;
  const people = peoplePolicy === 'none'
    ? 'no people, no hands, no faces'
    : peoplePolicy === 'required'
      ? handsRequired
        ? 'at most one participant; show only the minimum hand area needed for the supported interaction; natural pose; never make fingers the focal point'
        : 'at most one participant; hands are optional; if shown, use one natural interaction with anatomically plausible hands and no extra limbs'
      : handsRequired
        ? 'a participant is optional; if shown, include only the minimum natural hand interaction required by the paper'
        : 'people are optional; hands are optional; if shown, use one natural interaction with anatomically plausible hands and no extra limbs; prefer an object-led composition';
  return `${instructions[role] || instructions['artifact-closeup']}; ${people}`;
}

function communicationInstruction(body = {}) {
  const brief = body.communicationBrief || {};
  const audience = {
    public: 'target audience: general public; make the research subject immediately recognizable without relying on specialist notation',
    student: 'target audience: students; clearly show the research object together with the method or learning context',
    practitioner: 'target audience: practitioners; emphasize the usable artifact, workflow, equipment, or application setting',
    researcher: 'target audience: researchers; emphasize the paper-specific method, research object, and technically meaningful context',
  }[String(brief.audience || 'public').toLowerCase()];
  const goal = {
    'show-application': 'communication goal: show how the paper contribution is applied in a concrete, evidence-supported situation',
    'attract-attention': 'communication goal: create an immediate visual hook through one distinctive paper-specific subject and strong but credible composition',
    'science-explanation': 'communication goal: make the method or causal relationship understandable through a clear object-and-context composition, without an infographic',
    'spark-discussion': 'communication goal: show a concrete research situation with a visible tension, choice, or social implication that invites discussion without exaggeration',
  }[String(brief.communicationGoal || 'show-application').toLowerCase()];
  return [audience, goal].filter(Boolean).join('; ');
}

function enhancePublicationPhotoPrompt(prompt, body = {}, index = 0) {
  const base = sanitizeGeneratedPrompt(prompt);
  const roles = Array.isArray(body.compositionRoles) ? body.compositionRoles : [];
  const role = roles[index] || (index === 0 ? 'artifact-closeup' : 'study-context');
  const strategyPrompt = normalizeWhitespace(body.strategyPrompt || '');
  const outputProfile = OUTPUT_PROFILES[body.outputProfile] || OUTPUT_PROFILES['website-header'];
  const aestheticDirection = normalizeWhitespace(body.aestheticRecipe?.promptDirection || AESTHETIC_DIRECTIONS[body.aestheticProfile] || AESTHETIC_DIRECTIONS.auto);
  const styleDirection = normalizeWhitespace(body.styleRecipe?.promptDirection || '');
  return [
    base,
    strategyPrompt,
    styleDirection,
    aestheticDirection,
    communicationInstruction(body),
    outputProfile.instruction,
    ...concisePaperCues(body, role),
    compositionInstruction(role, body),
    'realistic academic editorial image, one obvious focal subject, physically plausible materials, no more than three visible paper-supported elements',
    'the first glance must reveal what is distinctive about this specific paper; avoid generic research stock photography',
    'no poster layout, no collage, no infographic, no typography, no readable text anywhere',
  ].filter(Boolean).join(', ').slice(0, 4000);
}

function negativeTerms(values) {
  const flattened = Array.isArray(values) ? values.flat(Infinity) : [values];
  return flattened.flatMap((value) => String(value || '').split(/[,;\n]+/))
    .map((term) => normalizeWhitespace(term))
    .filter((term) => term && term !== '...' && term.length > 1);
}

function compactNegativePrompt(groups, maxLength = 500) {
  const selected = [];
  const seen = new Set();
  for (const group of groups) {
    for (const term of negativeTerms(group)) {
      const key = term.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]/g, '');
      if (!key || seen.has(key)) continue;
      const candidate = [...selected, term].join(', ');
      if (candidate.length > maxLength) continue;
      selected.push(term);
      seen.add(key);
    }
  }
  return selected.join(', ');
}

function enhancePublicationNegativePrompt(negativePrompt, body = {}) {
  // Hands and screens can be valid paper-supported content. Exclude only visual errors and explicit unsupported content.
  const isPolicyOnlyRestriction = (term) => /\b(no|without|avoid)\s+(?:visible\s+)?hands?\b|\bhands?\s+unless\b|\bvisible\s+(?:hands?|fingers?)\b|\b(no|without|avoid)\s+(?:screens?|interfaces?|monitors?)\b/i.test(String(term || ''));
  const paperRestrictions = [
    ...(Array.isArray(body.mustAvoid) ? body.mustAvoid : []),
    body.strategyNegativePrompt,
    negativePrompt,
  ].filter((term) => !isPolicyOnlyRestriction(term));
  const priorityGroups = [
    ['readable text or labels', 'fake text or gibberish', 'random glyphs', 'logos or watermarks'],
    ['extra or missing fingers', 'malformed or fused fingers', 'distorted hands', 'deformed anatomy', 'uncanny face', 'extra limbs', 'duplicate body parts'],
    ['split screen', 'collage', 'poster layout', 'infographic', 'fabricated charts, equations, labels, or data visualizations'],
    ['unsupported objects or actions', 'generic laboratory stock photo', 'generic laptop scene', 'low quality', 'blurry', 'noisy'],
    paperRestrictions,
  ];
  return compactNegativePrompt(priorityGroups, 500);
}
function comfyRenderSize(width, height) {
  const requestedWidth = Math.max(512, Number(width) || 900);
  const requestedHeight = Math.max(512, Number(height) || 600);
  const targetWidth = requestedHeight > requestedWidth ? 896 : 1024;
  const targetHeight = Math.max(512, Math.round((targetWidth * requestedHeight / requestedWidth) / 8) * 8);
  return { width: targetWidth, height: targetHeight };
}
async function renderPhotoCandidateAttempt(body, index, seed, attempt) {
  const provider = requestedImageProvider(body);
  const articleSlug = safeSlug(body.articleSlug || body.title, 'publication');
  const rawCandidatePrompt = Array.isArray(body.prompts) && body.prompts[index]
    ? String(body.prompts[index])
    : body.prompt;
  const roles = Array.isArray(body.compositionRoles) ? body.compositionRoles : [];
  const compositionRole = roles[index] || (index === 0 ? 'artifact-closeup' : 'study-context');
  const initialPromptValidation = promptConflictResolution(rawCandidatePrompt, body, index);
  const expandedPrompt = enhancePublicationPhotoPrompt(initialPromptValidation.prompt, body, index);
  const finalPromptValidation = resolveHardPromptConflicts(expandedPrompt, body);
  if (finalPromptValidation.remainingConflicts.length) {
    throw new Error(`PROMPT_CONFLICT_UNRESOLVED: ${finalPromptValidation.remainingConflicts.join(', ')}`);
  }
  const candidatePrompt = finalPromptValidation.prompt;
  const promptValidation = {
    ...initialPromptValidation,
    finalConflicts: finalPromptValidation.conflicts,
    remainingConflicts: finalPromptValidation.remainingConflicts,
    conflictFree: true,
  };
  const candidateNegativePrompt = enhancePublicationNegativePrompt(body.negativePrompt, body);
  const filenamePrefix = `academic-header/${articleSlug}-${index + 1}`;
  const reference = body.referenceContext?.selected;
  const references = body.referenceContext?.selectedList || (reference ? [reference] : []);
  const uploadedReference = body.referenceContext?.uploadedName;
  const requestedStrength = Math.max(0.35, Math.min(Number(body.referenceStrength || ipAdapterDefaultStrength), 0.8));
  const referenceStrength = index === 0 ? requestedStrength : Math.max(0.4, requestedStrength - 0.1);
  const referenceDataUrl = body.referenceContext?.selectedDataUrl || '';
  const referenceDataUrls = body.referenceContext?.selectedDataUrls || (referenceDataUrl ? [referenceDataUrl] : []);
  let providerStrategy = provider === 'qwen-image'
    ? reference && referenceDataUrl ? 'qwen-reference' : 'qwen-text-to-image'
    : provider === 'local-comfyui'
      ? reference && uploadedReference ? 'ip-adapter' : 'text-to-image'
      : `${provider}-text-to-image`;
  let fallbackReason = '';
  const makeWorkflow = (width, height) => providerStrategy === 'ip-adapter'
    ? makeIpAdapterWorkflow({
      prompt: candidatePrompt,
      negativePrompt: candidateNegativePrompt,
      width,
      height,
      seed,
      filenamePrefix,
      referenceImage: uploadedReference,
      referenceStrength,
    })
    : makeComfyWorkflow({
      prompt: candidatePrompt,
      negativePrompt: candidateNegativePrompt,
      width,
      height,
      seed,
      filenamePrefix,
    });
  const generateAt = (width, height) => generateImageFromProvider({
    workflow: makeWorkflow(width, height),
    prompt: candidatePrompt,
    negativePrompt: candidateNegativePrompt,
    width,
    height,
    seed,
    referenceDataUrl: providerStrategy === 'qwen-reference' ? referenceDataUrl : '',
    referenceDataUrls: providerStrategy === 'qwen-reference' ? referenceDataUrls : [],
    qwenApiKey: body.qwenApiKey,
    requireRequestQwenKey: body.requireRequestQwenKey === true,
    imageApiKey: body.imageApiKey,
    imageModel: body.cloudImageModel,
    provider,
  });
  const renderSize = provider === 'local-comfyui' ? comfyRenderSize(body.width, body.height) : { width: body.width, height: body.height };
  let generated;
  try {
    generated = await generateAt(renderSize.width, renderSize.height);
  } catch (error) {
    const message = String(error.message || error);
    const memoryFailure = /out of memory|cuda|allocation|vram/i.test(message);
    if (providerStrategy === 'ip-adapter' && memoryFailure) {
      try {
        generated = await generateAt(832, 1109);
        fallbackReason = 'IP-Adapter used reduced resolution after a GPU memory error';
      } catch (reducedError) {
        if (body.referenceMode === 'required') throw reducedError;
        providerStrategy = 'text-to-image-fallback';
        fallbackReason = `IP-Adapter failed and used text-only generation: ${String(reducedError.message || reducedError).slice(0, 240)}`;
        generated = await generateAt(renderSize.width, renderSize.height);
      }
    } else if (providerStrategy === 'ip-adapter' && body.referenceMode !== 'required') {
      providerStrategy = 'text-to-image-fallback';
      fallbackReason = `IP-Adapter failed and used text-only generation: ${message.slice(0, 240)}`;
      generated = await generateAt(body.width, body.height);
    } else {
      throw error;
    }
  }
  const providerFileLabel = provider === 'qwen-image'
    ? 'qwen-photo'
    : provider === 'doubao-image'
      ? 'doubao-photo'
      : provider === 'openai-image'
        ? 'openai-photo'
        : 'comfy-photo';
  const baseName = `${articleSlug}-${providerFileLabel}-${index + 1}-${stableId({
    prompt: candidatePrompt,
    negativePrompt: candidateNegativePrompt,
    seed,
    checkpoint: comfyuiCheckpoint,
    attempt,
    compositionRole,
    providerStrategy,
    referenceSource: references.map((item) => item.id || item.fileName || item.url || '').filter(Boolean).join('|'),
  })}`;
  const fileName = `${baseName}.jpg`;
  const filePath = path.join(assetsDir, fileName);
  const rawOutput = await sharp(generated.buffer)
    .resize(body.width, body.height, { fit: 'cover', position: 'attention' })
    .jpeg({ quality: 90, mozjpeg: true })
    .toBuffer();
  const quality = await assessImageQuality(rawOutput, { ...body, compositionRole });
  const rawFileName = `${baseName}-raw.jpg`;
  await fs.writeFile(path.join(assetsDir, rawFileName), rawOutput);
  const output = body.outputProfile === 'xiaohongshu-cover'
    ? await renderXhsCover(rawOutput, {
        title: safeCoverHeadline(body.coverHeadline, body.title),
        label: body.strategyLabel || body.paperType || 'Academic research',
        showTitle: body.showTitle !== false,
        aiGenerated: true,
      })
    : rawOutput;
  await fs.writeFile(filePath, output);
  return {
    type: provider === 'qwen-image'
      ? `qwen-photo-${index + 1}`
      : provider === 'doubao-image'
        ? `doubao-photo-${index + 1}`
        : provider === 'openai-image'
          ? `openai-photo-${index + 1}`
          : providerStrategy === 'ip-adapter' ? `ip-adapter-photo-${index + 1}` : `comfy-photo-${index + 1}`,
    title: body.title || 'Publication header image',
    fileName,
    url: `${assetsPublicUrl}/${fileName}`,
    rawUrl: `${assetsPublicUrl}/${rawFileName}`,
    alt: body.alt || `Photorealistic visual for ${body.title || 'the publication'}`,
    suggestedPlacement: 'Use as the publication list thumbnail',
    aiGenerated: true,
    provider: generated.provider,
    providerStrategy,
    model: provider === 'qwen-image' ? String(body.cloudImageModel || qwenImageModel).trim()
      : ['doubao-image', 'openai-image'].includes(provider) ? String(body.cloudImageModel || '').trim()
      : comfyuiCheckpoint,
    generationVersion: body.generationVersion || generationVersion,
    referenceSource: references.map((item) => item.id || item.fileName || item.url || '').filter(Boolean).join('|'),
    referenceImage: reference ? { ...reference } : null,
    referenceImages: references.map((item) => ({ ...item })),
    referenceStrength: providerStrategy === 'ip-adapter' ? referenceStrength : null,
    fallbackReason,
    seed,
    promptId: generated.promptId,
    requestId: generated.requestId || '',
    providerRequest: generated.providerRequest || null,
    prompt: candidatePrompt,
    negativePrompt: candidateNegativePrompt,
    promptValidation,
    compositionRole,
    visualRole: compositionRole,
    attempts: attempt,
    qualityScore: quality.qualityScore,
    qualityWarnings: quality.qualityWarnings,
    qualityMetrics: quality.qualityMetrics,
    qualityPass: quality.qualityPass,
    textRisk: quality.textRisk,
    detectedText: quality.detectedText,
    semanticScore: quality.semanticScore,
    semanticLabels: quality.semanticLabels,
    semanticSignature: quality.semanticSignature,
    anatomyRisk: quality.anatomyRisk,
    handDetected: quality.handDetected,
    perceptualHash: quality.perceptualHash,
    colorSignature: quality.colorSignature,
  };
}
async function renderPhotoCandidate(body, index, requestedSeed) {
  const maxAttempts = Math.max(1, Math.min(Number(body.maxAttempts || 3), 3));
  const qualityMode = String(body.qualityMode || 'warn').toLowerCase();
  let bestImage = null;
  let lastImage = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const seed = attempt === 1 && Number.isSafeInteger(Number(requestedSeed))
      ? Number(requestedSeed)
      : randomSeed();
    const retryDirectives = attempt === 1 ? [] : [
      lastImage?.textRisk ? 'use only blank unmarked surfaces; crop out every screen, sign, document, label, plaque, inscription, and printed object' : '',
      ['review', 'reject'].includes(lastImage?.anatomyRisk) ? 'no people, no hands, no faces; frame only the research object and environment' : '',
    ].filter(Boolean);
    const attemptBody = retryDirectives.length ? {
      ...body,
      prompt: [body.prompt, ...retryDirectives].filter(Boolean).join(', '),
      prompts: (Array.isArray(body.prompts) ? body.prompts : [body.prompt]).map((prompt) => [prompt, ...retryDirectives].filter(Boolean).join(', ')),
      negativePrompt: [body.negativePrompt, 'readable text, labels, plaques, inscriptions, printed symbols, logos, watermarks, extra limbs, malformed fingers, distorted anatomy'].filter(Boolean).join(', '),
    } : body;
    const image = await renderPhotoCandidateAttempt(attemptBody, index, seed, attempt);
    lastImage = image;
    if (!bestImage || image.qualityScore > bestImage.qualityScore) bestImage = image;
    if (qualityMode === 'off' || image.qualityPass) return image;
  }
  return {
    ...bestImage,
    attempts: maxAttempts,
    qualityWarnings: [
      ...(bestImage?.qualityWarnings || []),
      { code: 'max-retries-reached', severity: 'warn', message: 'Returned the best candidate after local quality retries.' },
    ],
  };
}

function textUnits(text) {
  return [...String(text || '')].reduce((sum, char) => sum + (char.charCodeAt(0) > 127 ? 2 : 1), 0);
}

function truncateByUnits(text, maxUnits) {
  let result = '';
  let units = 0;
  for (const char of String(text || '')) {
    const nextUnits = units + (char.charCodeAt(0) > 127 ? 2 : 1);
    if (nextUnits > maxUnits) break;
    result += char;
    units = nextUnits;
  }
  return result;
}

function wrapText(text, maxChars = 20, maxLines = 4) {
  const source = String(text || '').trim();
  if (!source) return [''];
  const chunks = [];
  const words = source.split(/\s+/).filter(Boolean);

  if (words.length > 1) {
    let line = '';
    for (const word of words) {
      if (textUnits(word) > maxChars) {
        if (line) {
          chunks.push(line);
          line = '';
        }
        let rest = word;
        while (textUnits(rest) > maxChars) {
          const part = truncateByUnits(rest, maxChars);
          chunks.push(part);
          rest = rest.slice(part.length);
        }
        if (rest) line = rest;
        continue;
      }
      const next = line ? `${line} ${word}` : word;
      if (textUnits(next) > maxChars && line) {
        chunks.push(line);
        line = word;
      } else {
        line = next;
      }
    }
    if (line) chunks.push(line);
  } else {
    let line = '';
    for (const char of source) {
      const next = line + char;
      if (textUnits(next) > maxChars && line) {
        chunks.push(line);
        line = char;
      } else {
        line = next;
      }
    }
    if (line) chunks.push(line);
  }

  if (chunks.length <= maxLines) return chunks;
  const visible = chunks.slice(0, maxLines);
  const last = visible[visible.length - 1] || '';
  visible[visible.length - 1] = `${truncateByUnits(last, Math.max(4, maxChars - 3)).trimEnd()}...`;
  return visible;
}

function renderTextLines(lines, x, y, options = {}) {
  const {
    fontSize = 14,
    lineHeight = 18,
    fill = palette.ink,
    weight = 400,
    anchor = 'start',
    family = "'Noto Sans', 'Segoe UI', Arial, sans-serif",
  } = options;

  return `<text x="${x}" y="${y}" text-anchor="${anchor}" font-size="${fontSize}" font-weight="${weight}" fill="${fill}" font-family="${family}">${
    lines.map((line, index) => (
      `<tspan x="${x}" dy="${index === 0 ? 0 : lineHeight}">${esc(line)}</tspan>`
    )).join('')
  }</text>`;
}

function titleForDiagram(rawTitle) {
  const title = String(rawTitle || 'Publication Visual Summary').trim();
  if (textUnits(title) > 72) {
    return 'Publication Visual Summary';
  }
  return title;
}

function layoutDiagramNodes(nodes, width) {
  const topY = 178;
  const bottomY = 392;
  const cardWidth = Math.min(214, Math.max(178, Math.floor((width - 132) / Math.min(nodes.length, 4)) - 28));
  const laneGap = 28;
  const columnGap = Math.max(34, (width - 120 - cardWidth * Math.min(nodes.length, 4)) / Math.max(1, Math.min(nodes.length, 4) - 1));

  return nodes.map((node, index) => {
    const row = index < 4 ? 0 : 1;
    const rowIndex = row === 0 ? index : index - 4;
    const rowCount = row === 0 ? Math.min(nodes.length, 4) : nodes.length - 4;
    const gap = rowCount === 1 ? 0 : columnGap;
    const rowWidth = rowCount * cardWidth + Math.max(0, rowCount - 1) * gap;
    const x = (width - rowWidth) / 2 + rowIndex * (cardWidth + gap);
    const y = row === 0 ? topY : bottomY + laneGap;
    const titleLines = wrapText(node.title, 19, 3);
    const detailLines = wrapText(node.detail, 23, 4);
    const height = Math.max(132, 72 + titleLines.length * 19 + detailLines.length * 16);
    return {
      ...node,
      x,
      y,
      row,
      width: cardWidth,
      height,
      titleLines,
      detailLines,
    };
  });
}

function connectorPath(prev, next) {
  const prevRight = prev.x + prev.width;
  const prevMidY = prev.y + prev.height / 2;
  const nextMidY = next.y + next.height / 2;
  if (prev.row === next.row) {
    return `M ${prevRight + 8} ${prevMidY} C ${prevRight + 34} ${prevMidY}, ${next.x - 34} ${nextMidY}, ${next.x - 8} ${nextMidY}`;
  }
  const startX = prev.x + prev.width / 2;
  const endX = next.x + next.width / 2;
  const startY = prev.y + prev.height + 12;
  const endY = next.y - 12;
  return `M ${startX} ${startY} C ${startX} ${startY + 70}, ${endX} ${endY - 70}, ${endX} ${endY}`;
}

async function ensureAssetsDir() {
  await fs.mkdir(assetsDir, { recursive: true });
}

async function writeAssetSet(baseName, svg) {
  await ensureAssetsDir();
  const svgFile = `${baseName}.svg`;
  const pngFile = `${baseName}.png`;
  const svgPath = path.join(assetsDir, svgFile);
  const pngPath = path.join(assetsDir, pngFile);
  await fs.writeFile(svgPath, svg, 'utf8');
  await sharp(Buffer.from(svg)).png().toFile(pngPath);
  return {
    fileName: pngFile,
    svgFileName: svgFile,
    url: `${assetsPublicUrl}/${pngFile}`,
    svgUrl: `${assetsPublicUrl}/${svgFile}`,
    localPath: pngPath,
    localSvgPath: svgPath,
  };
}

function normalizeNodes(body) {
  const nodes = Array.isArray(body.nodes) ? body.nodes : [];
  const steps = Array.isArray(body.steps) ? body.steps : [];
  const concepts = Array.isArray(body.concepts) ? body.concepts : [];
  const source = nodes.length ? nodes : steps.length ? steps : concepts;
  if (source.length) {
    return source.slice(0, 8).map((item, index) => ({
      id: item.id || `n${index + 1}`,
      title: item.title || item.label || item.name || `Node ${index + 1}`,
      detail: item.detail || item.description || item.summary || '',
    }));
  }
  return [
    { id: 'n1', title: body.title || 'Research Question', detail: 'Input article content' },
    { id: 'n2', title: 'Method Design', detail: 'Extract variables and workflow' },
    { id: 'n3', title: 'Evidence Check', detail: 'Organize results and conclusions' },
  ];
}

function makeDrawioXml(nodes, title) {
  const cells = [
    '<mxfile host="academic-article-image-renderer"><diagram name="Page-1"><mxGraphModel><root>',
    '<mxCell id="0"/><mxCell id="1" parent="0"/>',
  ];
  nodes.forEach((node, index) => {
    const x = 60 + index * 220;
    cells.push(
      `<mxCell id="${esc(node.id)}" value="${esc(node.title)}" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#eff6ff;strokeColor=#2563eb;" vertex="1" parent="1"><mxGeometry x="${x}" y="120" width="170" height="76" as="geometry"/></mxCell>`
    );
    if (index > 0) {
      cells.push(
        `<mxCell id="e${index}" edge="1" parent="1" source="${esc(nodes[index - 1].id)}" target="${esc(node.id)}"><mxGeometry relative="1" as="geometry"/></mxCell>`
      );
    }
  });
  cells.push('</root></mxGraphModel></diagram></mxfile>');
  return cells.join('');
}

function cardWordsFrom(value) {
  return String(value || '')
    .replace(/[^A-Za-z0-9\u4e00-\u9fff-]/g, ' ')
    .split(/\s+/)
    .map((word) => word.trim())
    .filter((word) => textUnits(word) >= 4)
    .filter((word) => !/^(using|based|study|paper|research|method|system|toward|towards|through|among|with|from|this|that|and|for|the|of|in|on|to|by|an|a)$/i.test(word));
}

function uniqueCardTerms(values, maxItems = 5) {
  const seen = new Set();
  const output = [];
  for (const value of values) {
    for (const word of Array.isArray(value) ? value : cardWordsFrom(value)) {
      const key = String(word).toLowerCase();
      if (!key || seen.has(key)) continue;
      seen.add(key);
      output.push(shortLabel(word, word, 22));
      if (output.length >= maxItems) return output;
    }
  }
  return output;
}

function academicCardTheme(mode = '') {
  const key = String(mode || '').toLowerCase();
  if (/heritage|museum|ar-navigation|vr-museum/.test(key)) {
    return { accent: '#8aa1a0', accent2: '#d9b16c', soft: '#eef4f2', label: 'DIGITAL HERITAGE' };
  }
  if (/audio|soundscape/.test(key)) {
    return { accent: '#7c9bb3', accent2: '#b98f73', soft: '#eef4f8', label: 'AUDIO RESEARCH' };
  }
  if (/algorithm|computational|data/.test(key)) {
    return { accent: '#7890a8', accent2: '#86a77a', soft: '#f0f4f7', label: 'METHOD PREVIEW' };
  }
  if (/haptic|wearable|biosensing|rehab/.test(key)) {
    return { accent: '#8b9f75', accent2: '#ba8d85', soft: '#f2f6ef', label: 'DEVICE STUDY' };
  }
  if (/driving|field/.test(key)) {
    return { accent: '#8793a1', accent2: '#c1a15e', soft: '#f3f4f5', label: 'FIELD STUDY' };
  }
  return { accent: '#7f97ad', accent2: '#8cab91', soft: '#f2f5f7', label: 'PUBLICATION PREVIEW' };
}

function renderAcademicCardSvg(body) {
  const mode = body.visualMode || body.visualStrategy || '';
  const theme = academicCardTheme(mode);
  const title = String(body.title || 'Publication Preview').trim();
  const compactTitle = title.length > 74 ? 'Publication thumbnail' : title;
  const titleLines = wrapText(compactTitle, 30, 2);
  const venue = shortLabel(body.venue || body.strategyLabel || mode || 'Academic publication', 'Academic publication', 36);
  const brief = body.visualBrief && typeof body.visualBrief === 'object' ? body.visualBrief : {};
  const fingerprint = body.visualFingerprint && typeof body.visualFingerprint === 'object' ? body.visualFingerprint : {};
  const anchors = uniqueCardTerms([
    Array.isArray(body.visualAnchors) ? body.visualAnchors : [],
    Array.isArray(fingerprint.distinctiveTerms) ? fingerprint.distinctiveTerms : [],
    brief.technology,
    brief.subject,
    title,
  ], 3);
  while (anchors.length < 3) anchors.push(['object', 'scene', 'method'][anchors.length]);

  const modeLabel = esc(theme.label);
  const accent = theme.accent;
  const accent2 = theme.accent2;
  const soft = theme.soft;
  const chipText = anchors.slice(0, 3).map((term) => esc(shortLabel(term, term, 16))).join(' / ');
  const titleSvg = renderTextLines(titleLines, 70, 186, { fontSize: 29, lineHeight: 36, fill: '#27313a', weight: 760 });

  return '<?xml version="1.0" encoding="UTF-8"?>\n'
    + '<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600">'
    + '<defs><linearGradient id="bg" x1="0" x2="1" y1="0" y2="1"><stop offset="0" stop-color="#fbfcfd"/><stop offset="1" stop-color="' + soft + '"/></linearGradient><filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="14" stdDeviation="20" flood-color="#334155" flood-opacity="0.09"/></filter></defs>'
    + '<rect width="900" height="600" fill="url(#bg)"/>'
    + '<rect x="54" y="54" width="792" height="492" rx="18" fill="#ffffff" stroke="#e3e8ed"/>'
    + '<rect x="54" y="54" width="8" height="492" rx="4" fill="' + accent + '"/>'
    + '<text x="70" y="104" font-size="12" font-weight="800" fill="' + accent + '" font-family="Noto Sans, Segoe UI, Arial, sans-serif">' + modeLabel + '</text>'
    + '<text x="70" y="132" font-size="13" font-weight="650" fill="#7a858f" font-family="Noto Sans, Segoe UI, Arial, sans-serif">' + esc(venue) + '</text>'
    + titleSvg
    + '<text x="70" y="432" font-size="14" font-weight="700" fill="#66717b" font-family="Noto Sans, Segoe UI, Arial, sans-serif">' + chipText + '</text>'
    + '<line x1="70" y1="456" x2="494" y2="456" stroke="#e5eaee"/>'
    + '<g filter="url(#shadow)">'
    + '<rect x="574" y="146" width="220" height="220" rx="42" fill="' + soft + '" stroke="#e2e8ed"/>'
    + '<circle cx="684" cy="256" r="72" fill="#ffffff" stroke="' + accent + '" stroke-width="6"/>'
    + '<path d="M642 276 C665 226, 708 226, 730 276" fill="none" stroke="' + accent2 + '" stroke-width="12" stroke-linecap="round"/>'
    + '<circle cx="653" cy="235" r="12" fill="' + accent + '" fill-opacity="0.82"/>'
    + '<circle cx="715" cy="235" r="12" fill="' + accent + '" fill-opacity="0.62"/>'
    + '<circle cx="684" cy="296" r="15" fill="' + accent2 + '" fill-opacity="0.78"/>'
    + '</g>'
    + '<text x="684" y="416" text-anchor="middle" font-size="14" font-weight="800" fill="#5c6872" font-family="Noto Sans, Segoe UI, Arial, sans-serif">' + esc(shortLabel(anchors[0] || theme.label, 'research', 18)) + '</text>'
    + '</svg>';
}
async function renderAcademicCardCandidate(body) {
  const articleSlug = safeSlug(body.articleSlug || body.title, 'publication');
  const svg = renderEditorialSatoriSvg(body, 'academic');
  const baseName = articleSlug + '-academic-card-' + stableId({
    title: body.title,
    venue: body.venue,
    visualMode: body.visualMode || body.visualStrategy,
    visualAnchors: body.visualAnchors,
    visualBrief: body.visualBrief,
  });
  const svgFileName = baseName + '.svg';
  const fileName = baseName + '.jpg';
  await ensureAssetsDir();
  await fs.writeFile(path.join(assetsDir, svgFileName), svg, 'utf8');
  const rawOutput = await sharp(Buffer.from(svg)).resize(XHS_COVER_SIZE.width, XHS_COVER_SIZE.height).jpeg({ quality: 92, mozjpeg: true }).toBuffer();
  const rawFileName = baseName + '-raw.jpg';
  await fs.writeFile(path.join(assetsDir, rawFileName), rawOutput);

  await fs.writeFile(path.join(assetsDir, fileName), rawOutput);

  return {
    type: 'academic-card',
    title: body.title || 'Publication academic card',
    fileName,
    url: assetsPublicUrl + '/' + fileName,
    rawUrl: assetsPublicUrl + '/' + rawFileName,
    svgUrl: assetsPublicUrl + '/' + svgFileName,
    alt: body.alt || 'Text-safe academic thumbnail for ' + (body.title || 'the publication'),
    suggestedPlacement: 'Use as the publication list thumbnail',
    aiGenerated: false,
    provider: 'renderer-api',
    model: 'deterministic-svg-card-v3-editorial',
    seed: null,
    prompt: '',
    qualityScore: 1,
    qualityWarnings: [],
    attempts: 1,
    visualMode: body.visualMode || body.visualStrategy || 'academic-card',
  };
}

function renderDiagramSvg(body) {
  const title = titleForDiagram(body.title || 'Academic Structure Diagram');
  const nodes = normalizeNodes(body);
  const count = nodes.length;
  const width = count > 4 ? 1120 : Math.max(920, 230 * count + 120);
  const colors = [palette.blue, palette.green, palette.amber, palette.violet, palette.rose];
  const laidOutNodes = layoutDiagramNodes(nodes, width);
  const maxNodeBottom = Math.max(...laidOutNodes.map((node) => node.y + node.height), 330);
  const height = maxNodeBottom + 82;
  const titleLines = wrapText(title, 58, 2);
  const subtitleLines = wrapText(body.subtitle || 'Visual summary generated from the publication metadata', 74, 2);

  const connectorSvgs = laidOutNodes.slice(1).map((node, index) => {
    const previous = laidOutNodes[index];
    return `<path d="${connectorPath(previous, node)}" fill="none" stroke="${palette.border}" stroke-width="2" marker-end="url(#arrow)"/>`;
  }).join('');

  const nodeSvgs = laidOutNodes.map((node, index) => {
    const color = colors[index % colors.length];
    const titleY = node.y + 54;
    const detailY = titleY + node.titleLines.length * 19 + 14;
    return `
      <rect x="${node.x}" y="${node.y}" width="${node.width}" height="${node.height}" rx="6" fill="#ffffff" stroke="${palette.border}" stroke-width="1.4"/>
      <rect x="${node.x}" y="${node.y}" width="${node.width}" height="6" rx="3" fill="${color}"/>
      <circle cx="${node.x + 25}" cy="${node.y + 31}" r="13" fill="#ffffff" stroke="${color}" stroke-width="2"/>
      <text x="${node.x + 25}" y="${node.y + 36}" text-anchor="middle" font-size="13" font-weight="700" fill="${color}" font-family="'Noto Sans', 'Segoe UI', Arial, sans-serif">${index + 1}</text>
      ${renderTextLines(node.titleLines, node.x + 18, titleY, { fontSize: 15, lineHeight: 19, fill: palette.ink, weight: 700 })}
      ${renderTextLines(node.detailLines, node.x + 18, detailY, { fontSize: 12.5, lineHeight: 16, fill: palette.muted })}
    `;
  }).join('');

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="${palette.border}"/>
    </marker>
  </defs>
  <rect width="100%" height="100%" fill="${palette.paper}"/>
  <rect x="28" y="28" width="${width - 56}" height="${height - 56}" rx="8" fill="#ffffff" stroke="${palette.border}"/>
  ${renderTextLines(titleLines, 60, 74, { fontSize: 23, lineHeight: 28, fill: palette.ink, weight: 750 })}
  ${renderTextLines(subtitleLines, 60, titleLines.length > 1 ? 124 : 108, { fontSize: 14, lineHeight: 18, fill: palette.muted })}
  <line x1="60" y1="148" x2="${width - 60}" y2="148" stroke="${palette.border}"/>
  ${connectorSvgs}
  ${nodeSvgs}
  <text x="${width - 60}" y="${height - 40}" text-anchor="end" font-size="11.5" fill="${palette.muted}" font-family="'Noto Sans', 'Segoe UI', Arial, sans-serif">publication visual summary</text>
</svg>`;
}

function renderCarbonSvg(body) {
  const code = String(body.code || '').slice(0, 8000);
  const language = body.language || 'plaintext';
  const title = body.title || 'Code';
  const lines = code.split('\n');
  const maxLineLength = Math.max(...code.split('\n').map((line) => line.length), 24);
  const width = Math.min(1400, Math.max(760, maxLineLength * 9 + 120));
  const height = Math.min(1800, Math.max(320, lines.length * 24 + 132));
  const lineSvgs = lines.map((line, index) => {
    const y = 116 + index * 24;
    return `<text x="84" y="${y}" font-size="15" fill="#dbeafe" font-family="'Noto Sans Mono', 'Consolas', monospace">${esc(line || ' ')}</text>`;
  }).join('');
  const numbers = lines.map((_, index) => (
    `<text x="48" y="${116 + index * 24}" text-anchor="end" font-size="13" fill="#64748b" font-family="'Noto Sans Mono', 'Consolas', monospace">${index + 1}</text>`
  )).join('');

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="100%" height="100%" rx="0" fill="#e2e8f0"/>
  <rect x="28" y="28" width="${width - 56}" height="${height - 56}" rx="10" fill="#0f172a"/>
  <circle cx="58" cy="58" r="7" fill="#ef4444"/>
  <circle cx="80" cy="58" r="7" fill="#f59e0b"/>
  <circle cx="102" cy="58" r="7" fill="#22c55e"/>
  <text x="${width / 2}" y="64" text-anchor="middle" font-size="14" fill="#94a3b8" font-family="'Noto Sans', sans-serif">${esc(title)} · ${esc(language)}</text>
  <line x1="28" y1="84" x2="${width - 28}" y2="84" stroke="#1e293b"/>
  ${numbers}
  ${lineSvgs}
</svg>`;
}

app.post('/extract/pdf', async (req, res, next) => {
  try {
    const paperId = String(req.query.id || req.headers['x-paper-id'] || 'form-paper');
    const result = await extractPdfPackageCached(req.body, paperId);
    res.json({
      ...result,
      extractedAt: new Date().toISOString(),
    });
  } catch (error) {
    next(error);
  }
});

app.post('/quality/evaluate', async (req, res, next) => {
  try {
    const body = req.body || {};
    const imageUrl = String(body.imageUrl || body.url || '').trim();
    if (!imageUrl) {
      res.status(400).json({ error: 'INVALID_QUALITY_REQUEST', message: 'imageUrl is required' });
      return;
    }
    const response = await fetchWithTimeout(imageUrl, {}, 60000);
    if (!response.ok) throw new Error(`Image download returned ${response.status}`);
    const buffer = Buffer.from(await response.arrayBuffer());
    res.json(await assessImageQuality(buffer, body));
  } catch (error) {
    next(error);
  }
});
app.post('/prompt/validate', (req, res) => {
  const body = req.body || {};
  const prompts = Array.isArray(body.prompts)
    ? body.prompts
    : [body.prompt, body.alternativePrompt].filter(Boolean);
  res.json({
    ok: true,
    results: prompts.map((prompt, index) => {
      const initial = promptConflictResolution(prompt, body, index);
      const expanded = enhancePublicationPhotoPrompt(initial.prompt, body, index);
      const final = resolveHardPromptConflicts(expanded, body);
      return {
        ...initial,
        finalPrompt: final.prompt,
        finalConflicts: final.conflicts,
        remainingConflicts: final.remainingConflicts,
        conflictFree: final.remainingConflicts.length === 0,
        negativePrompt: enhancePublicationNegativePrompt(body.negativePrompt, body),
      };
    })
  });
});

app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    comfyuiBaseUrl,
    comfyuiCheckpoint,
    comfyuiSteps,
    comfyuiCfg,
    imageProvider,
    imageModel: imageProvider === 'qwen-image' ? qwenImageModel : comfyuiCheckpoint,
    qwenConfigured: Boolean(qwenApiKey),
    generationVersion,
    ocrEnabled,
    localVisionEnabled,
    visionModel,
    layoutEngine: 'vercel-satori',
  });
});

app.post('/render/academic-card', async (req, res, next) => {
  try {
    await ensureAssetsDir();
    const body = req.body || {};
    const image = await renderAcademicCardCandidate(body);
    res.json({
      articleTitle: body.title || 'Untitled Publication',
      articleSlug: safeSlug(body.articleSlug || body.title, 'publication'),
      generatedAt: new Date().toISOString(),
      contentSource: body.contentSource || 'metadata',
      contentMode: body.contentMode || 'metadata',
      visualBrief: body.visualBrief || {},
      visualMode: body.visualMode || body.visualStrategy || 'academic-card',
      visualFingerprint: body.visualFingerprint || {},
      images: [image],
    });
  } catch (error) {
    next(error);
  }
});

app.post('/render/xhs-cover', async (req, res, next) => {
  try {
    await ensureAssetsDir();
    const input = req.body || {};
    const base = await readReferenceBuffer({
      url: input.url,
      dataUrl: input.imageDataUrl,
      fileName: input.fileName,
    });
    const fallback = base || await renderFallbackBackground({ anchors: input.uniqueAnchors || [] });
    const output = await renderXhsCover(fallback, {
      title: safeCoverHeadline(input.coverHeadline, input.title),
      label: input.strategyLabel || input.paperType || 'Academic research',
      showTitle: input.showTitle !== false,
      aiGenerated: Boolean(input.aiGenerated),
      sourceLabel: input.sourceLabel || '',
      textColor: input.textColor,
      backgroundColor: input.backgroundColor,
      templatePreset: input.templatePreset,
      backgroundMode: input.backgroundMode,
    });
    const fileName = `${safeSlug(input.articleSlug || input.title, 'paper')}-xhs-cover-${stableId({
      title: input.coverHeadline || input.title,
      source: input.url || input.fileName || String(input.imageDataUrl || '').slice(0, 80),
      templatePreset: input.templatePreset || 'classic',
    })}.jpg`;
    await fs.writeFile(path.join(assetsDir, fileName), output);
    res.json({
      image: {
        type: input.type || 'xhs-cover',
        url: `${assetsPublicUrl}/${fileName}`,
        fileName,
        aiGenerated: Boolean(input.aiGenerated),
        provider: 'satori-layout',
        model: 'vercel-satori+sharp-xhs-v4',
        generationVersion,
      },
      imageDataBase64: output.toString('base64'),
    });
  } catch (error) {
    next(error);
  }
});

async function renderSafeFallbackCandidate(body, index, reason) {
  const role = body.compositionRoles?.[index] || (index === 0 ? 'artifact-closeup' : 'study-context');
  const background = await renderFallbackBackground({
    anchors: uniqueStrings(body.uniqueAnchors || body.mustShow || [], 3),
  });
  const output = await renderXhsCover(background, {
    title: safeCoverHeadline(body.coverHeadline, body.title),
    label: body.strategyLabel || body.paperType || 'Academic research',
    showTitle: body.showTitle !== false,
    sourceLabel: '基于论文原文整理 · 安全模板',
  });
  const fileName = `${safeSlug(body.articleSlug || body.title, 'paper')}-xhs-template-${index + 1}-${stableId({
    role,
    title: body.coverHeadline || body.title,
    anchors: body.uniqueAnchors,
  })}.jpg`;
  await fs.writeFile(path.join(assetsDir, fileName), output);
  return {
    type: `xhs-template-${index + 1}`,
    title: body.title || 'Paper cover',
    fileName,
    url: `${assetsPublicUrl}/${fileName}`,
    alt: body.alt || `Evidence-grounded cover for ${body.title || 'the paper'}`,
    aiGenerated: false,
    provider: 'satori-layout',
    providerStrategy: 'safe-template-fallback',
    model: 'vercel-satori+sharp-xhs-v4',
    generationVersion,
    compositionRole: role,
    visualRole: role,
    seed: null,
    attempts: 1,
    qualityScore: 1,
    qualityWarnings: [],
    qualityMetrics: { width: XHS_COVER_SIZE.width, height: XHS_COVER_SIZE.height },
    qualityPass: true,
    textRisk: false,
    anatomyRisk: 'none',
    semanticScore: null,
    fallbackReason: String(reason || '').slice(0, 360),
  };
}

async function renderSourceCoverCandidate(body, reference, index) {
  const sourceBuffer = await readReferenceBuffer(reference);
  if (!sourceBuffer) throw new Error('Source image could not be read');
  const assessment = await classifyReferenceImage(sourceBuffer, reference);
  const preserveWholeImage = ['diagram', 'chart-table', 'interface'].includes(assessment.imageType);
  let coverBase = sourceBuffer;
  if (preserveWholeImage) {
    const contained = await sharp(sourceBuffer)
      .rotate()
      .resize(1080, 900, { fit: 'contain', background: '#ffffff' })
      .jpeg({ quality: 92, mozjpeg: true })
      .toBuffer();
    coverBase = await sharp({
      create: { width: XHS_COVER_SIZE.width, height: XHS_COVER_SIZE.height, channels: 3, background: '#eef2f4' },
    })
      .composite([{ input: contained, left: 81, top: 90 }])
      .jpeg({ quality: 92, mozjpeg: true })
      .toBuffer();
  }
  const pageLabel = reference.pageNumber ? ` · 第${reference.pageNumber}页` : '';
  const output = await renderXhsCover(coverBase, {
    title: safeCoverHeadline(body.coverHeadline, body.title),
    label: body.strategyLabel || body.paperType || '论文原图',
    showTitle: body.showTitle !== false,
    aiGenerated: false,
    sourceLabel: `论文原图${pageLabel} · 内容未经AI修改`,
  });
  const baseName = `${safeSlug(body.articleSlug || body.title, 'paper')}-source-cover-${index + 1}-${stableId({
    source: reference.fileName || reference.url || reference.id,
    title: body.coverHeadline || body.title,
  })}`;
  const fileName = `${baseName}.jpg`;
  await fs.writeFile(path.join(assetsDir, fileName), output);
  return {
    type: 'source-cover',
    title: body.title || 'Publication source cover',
    fileName,
    url: `${assetsPublicUrl}/${fileName}`,
    rawUrl: reference.url || '',
    originalFileName: reference.fileName || '',
    alt: reference.caption || body.sourceImageAlt || `Publication source image for ${body.title || 'the paper'}`,
    caption: reference.caption || '',
    pageNumber: reference.pageNumber || null,
    imageType: assessment.imageType,
    aiGenerated: false,
    provider: 'publication-source-template',
    providerStrategy: 'source-template',
    model: 'vercel-satori+sharp-xhs-v4',
    qualityScore: 1,
    qualityWarnings: assessment.referenceWarnings || [],
    semanticScore: null,
    anatomyRisk: 'none',
    attempts: 1,
  };
}

app.post('/render/photo', async (req, res, next) => {
  try {
    await ensureAssetsDir();
    const input = req.body || {};
    const prompt = String(input.prompt || '').trim();
    if (!prompt) {
      res.status(400).json({
        error: 'INVALID_PHOTO_REQUEST',
        message: 'prompt is required',
      });
      return;
    }
    const requestQwenApiKey = String(input.qwenApiKey || '').trim();
    const requestImageApiKey = String(input.imageApiKey || requestQwenApiKey || '').trim();
    const body = {
      ...input,
      qwenApiKey: requestQwenApiKey,
      imageApiKey: requestImageApiKey,
      requireRequestQwenKey: input.requireRequestQwenKey === true || input.requireRequestImageKey === true,
      prompt: prompt.slice(0, 4000),
      prompts: Array.isArray(input.prompts)
        ? input.prompts.map((candidatePrompt) => String(candidatePrompt || '').trim().slice(0, 4000)).filter(Boolean).slice(0, 2)
        : [],
      negativePrompt: String(input.negativePrompt || 'readable text, fake text, gibberish, random glyphs, words, letters, numbers, logos, watermarks, malformed hands, low quality, blurry').slice(0, 2400),
      mustShow: uniqueStrings(input.mustShow || [], 3),
      mustAvoid: uniqueStrings(input.mustAvoid || [], 16),
      communicationBrief: input.communicationBrief && typeof input.communicationBrief === 'object' ? input.communicationBrief : {},
      outputProfile: ['website-header', 'xiaohongshu-cover'].includes(String(input.outputProfile || '').toLowerCase()) ? String(input.outputProfile).toLowerCase() : 'website-header',
      aestheticProfile: Object.hasOwn(AESTHETIC_DIRECTIONS, String(input.aestheticProfile || '').toLowerCase()) ? String(input.aestheticProfile).toLowerCase() : 'auto',
      styleRecipe: input.styleRecipe && typeof input.styleRecipe === 'object' ? input.styleRecipe : {},
      aestheticRecipe: input.aestheticRecipe && typeof input.aestheticRecipe === 'object' ? input.aestheticRecipe : {},
      outputRecipe: input.outputRecipe && typeof input.outputRecipe === 'object' ? input.outputRecipe : {},
      handsRequired: input.handsRequired === true,
      uniqueAnchors: uniqueStrings(input.uniqueAnchors || [], 10),
      compositionRoles: uniqueStrings(input.compositionRoles || ['artifact-closeup', 'study-context'], 2),
      peoplePolicy: ['none', 'optional', 'required'].includes(String(input.peoplePolicy || '').toLowerCase())
        ? String(input.peoplePolicy).toLowerCase()
        : 'optional',
      riskProfile: input.riskProfile && typeof input.riskProfile === 'object' ? input.riskProfile : {},
      qualityMode: ['warn', 'strict', 'off'].includes(String(input.qualityMode || '').toLowerCase())
        ? String(input.qualityMode).toLowerCase()
        : 'warn',
      maxAttempts: Math.max(1, Math.min(Number(input.maxAttempts || 3), 3)),
      count: Math.max(1, Math.min(Number(input.count || 2), 2)),
      width: Math.max(512, Math.min(Number(input.width || (String(input.outputProfile).toLowerCase() === 'xiaohongshu-cover' ? XHS_COVER_SIZE.width : 900)), 2048)),
      height: Math.max(512, Math.min(Number(input.height || (String(input.outputProfile).toLowerCase() === 'xiaohongshu-cover' ? XHS_COVER_SIZE.height : 600)), 2048)),
      coverHeadline: safeCoverHeadline(input.coverHeadline, input.title),
      showTitle: input.showTitle !== false,
      referenceMode: ['auto', 'off', 'required'].includes(String(input.referenceMode || '').toLowerCase())
        ? String(input.referenceMode).toLowerCase()
        : 'auto',
      referenceImages: Array.isArray(input.referenceImages) ? input.referenceImages.slice(0, 5) : [],
      sourceFigures: Array.isArray(input.sourceFigures) ? input.sourceFigures.slice(0, 5) : [],
      sourceImage: String(input.sourceImage || '').trim(),
      referenceStrength: Math.max(0.35, Math.min(Number(input.referenceStrength || ipAdapterDefaultStrength), 0.8)),
    };
    const providerModeInput = String(input.imageProviderMode || input.image_provider || 'automatic').trim().toLowerCase();
    const providerMode = ['automatic', 'qwen', 'comfyui', 'dual'].includes(providerModeInput)
      ? providerModeInput
      : 'automatic';
    const cloudProvider = ({ qwen: 'qwen-image', openai: 'openai-image', doubao: 'doubao-image' })[String(input.cloudImageProvider || 'qwen').toLowerCase()] || 'qwen-image';
    const providers = providerMode === 'dual'
      ? [cloudProvider, 'local-comfyui']
      : [providerMode === 'comfyui' ? 'local-comfyui' : providerMode === 'qwen' ? cloudProvider : cloudProvider];
    const images = [];
    const generationWarnings = [];
    const requestedSeeds = Array.isArray(body.seeds) ? body.seeds : [];
    async function generateProviderCandidate(provider, index) {
      const providerBody = { ...body, providerOverride: provider };
      const seed = Number.isSafeInteger(Number(requestedSeeds[index]))
        ? Number(requestedSeeds[index])
        : randomSeed();
      try {
        providerBody.referenceContext = await prepareReferenceContext(providerBody);
        body.referenceContext ||= providerBody.referenceContext;
        return await renderPhotoCandidate(providerBody, index, seed);
      } catch (error) {
        const failure = `${provider}: ${String(error.message || error).slice(0, 260)}`;
        if (provider === 'qwen-image' && providers.includes('local-comfyui')) {
          try {
            const fallbackBody = { ...body, providerOverride: 'local-comfyui' };
            fallbackBody.referenceContext = await prepareReferenceContext(fallbackBody);
            const fallback = await renderPhotoCandidate(fallbackBody, index, randomSeed());
            generationWarnings.push(`Qwen candidate ${index + 1} used a ComfyUI image fallback.`);
            return {
              ...fallback,
              providerStrategy: `${fallback.providerStrategy || 'text-to-image'}-qwen-fallback`,
              fallbackReason: `Qwen was unavailable; ComfyUI generated this image instead. ${failure}`,
            };
          } catch (fallbackError) {
            generationWarnings.push(`${failure}; ComfyUI fallback also failed: ${String(fallbackError.message || fallbackError).slice(0, 260)}`);
            return null;
          }
        }
        generationWarnings.push(failure);
        return null;
      }
    }
    async function generateDistinctProviders(providerList) {
      const results = await Promise.all(providerList.map((provider, index) => generateProviderCandidate(provider, index)));
      images.push(...results.filter(Boolean));
    }
    if (['algorithm-computational', 'review-survey', 'algorithm-hybrid'].includes(body.visualMode)) {
      const explicitImageProvider = ['qwen', 'comfyui', 'dual'].includes(providerMode);
      const visualStyle = String(body.visualStyle || '').toLowerCase();
      // Every paper type gets at least one generated-image candidate, including computational papers.
      const includeComputationalPhoto = true;
      // An explicit generator choice must win over the conservative method-template default.
      if (!explicitImageProvider || visualStyle === 'clean-concept') {
        images.push(await renderMethodSummaryCandidate(body));
      }
      if (includeComputationalPhoto) {
        if (providers.length > 1) {
          await generateDistinctProviders(providers);
        } else {
          const generated = await generateProviderCandidate(providers[0], 0);
          if (generated) images.push(generated);
        }
      }
    } else if (providerMode === 'dual') {
      await generateDistinctProviders(providers);
    } else {
      for (let index = 0; index < body.count; index += 1) {
        const generated = await generateProviderCandidate(providers[0], index);
        if (generated) images.push(generated);
      }
    }
    const doubaoFailure = cloudProvider === 'doubao-image'
      ? generationWarnings.find((warning) => warning.startsWith('doubao-image:'))
      : '';
    if (doubaoFailure && images.length === 0) {
      res.status(502).json({
        error: 'DOUBAO_IMAGE_GENERATION_FAILED',
        message: `Doubao Seedream generation failed. Verify the Ark API key, endpoint ID, and model, then retry. ${doubaoFailure.slice(0, 320)}`,
      });
      return;
    }
    const sourceCovers = [];
    if (body.sourcePolicy !== 'ai-only' && body.showTitle !== false) {
      const sourceInputs = [
        ...(body.sourceImage ? [{ type: 'source-original', url: body.sourceImage, caption: body.sourceImageAlt || '' }] : []),
        ...(Array.isArray(body.sourceFigures) ? body.sourceFigures : []),
      ];
      const seenSources = new Set();
      const rankedSources = sourceInputs
        .filter((source) => {
          const key = String(source.fileName || source.url || source.id || '');
          if (!key || seenSources.has(key)) return false;
          seenSources.add(key);
          return true;
        })
        .sort((a, b) => Number(b.relevanceScore || b.sourceScore || 0) - Number(a.relevanceScore || a.sourceScore || 0))
        .slice(0, 3);
      for (const [index, source] of rankedSources.entries()) {
        try {
          sourceCovers.push(await renderSourceCoverCandidate(body, source, index));
        } catch {}
      }
    }
    res.json({
      articleTitle: body.title || 'Untitled Publication',
      articleSlug: safeSlug(body.articleSlug || body.title, 'publication'),
      generatedAt: new Date().toISOString(),
      imageProviderMode: providerMode,
      providersUsed: [...new Set(images.map((image) => image.provider).filter(Boolean))],
      generationWarnings,
      prompt: body.prompt,
      prompts: body.prompts,
      negativePrompt: body.negativePrompt,
      visualBrief: body.visualBrief || {},
      visualMode: body.visualMode || 'system-interface',
      evidence: body.evidence || [],
      mustShow: body.mustShow || [],
      mustAvoid: body.mustAvoid || [],
      uniqueAnchors: body.uniqueAnchors || [],
      compositionRoles: body.compositionRoles || [],
      peoplePolicy: body.peoplePolicy || 'optional',
      handsRequired: body.handsRequired === true,
      communicationBrief: body.communicationBrief || {},
      outputProfile: body.outputProfile,
      aestheticProfile: body.aestheticProfile,
      visualStyle: body.visualStyle || 'auto',
      methodSummary: body.methodSummary || null,
      referenceMode: body.referenceMode,
      referenceStrength: body.referenceStrength,
      referenceImages: body.referenceContext?.references || [],
      referenceSelection: body.referenceContext?.selected || null,
      referenceSelections: body.referenceContext?.selectedList || [],
      providerStrategy: images.some((image) => image.providerStrategy === 'qwen-reference')
        ? 'qwen-reference'
        : images.some((image) => image.providerStrategy === 'qwen-text-to-image')
          ? 'qwen-text-to-image'
          : images.some((image) => image.providerStrategy === 'ip-adapter')
            ? 'ip-adapter'
            : images.some((image) => image.type === 'method-summary') ? 'method-summary' : 'text-to-image',
      coverHeadline: body.coverHeadline,
      generationVersion: body.generationVersion || generationVersion,
      contentSource: body.contentSource || 'abstract',
      contentMode: body.contentMode || 'abstract',
      sourceCovers,
      images,
    });
  } catch (error) {
    next(error);
  }
});

app.post('/render/drawio', async (req, res, next) => {
  try {
    const body = req.body || {};
    const baseName = `${safeSlug(body.articleSlug || body.title, 'diagram')}-${stableId(body)}`;
    const svg = renderDiagramSvg(body);
    const assets = await writeAssetSet(baseName, svg);
    const drawioXml = body.drawioXml || makeDrawioXml(normalizeNodes(body), body.title);
    const drawioFileName = `${baseName}.drawio`;
    await fs.writeFile(path.join(assetsDir, drawioFileName), drawioXml, 'utf8');
    res.json({
      type: 'drawio',
      ...assets,
      drawioUrl: `${assetsPublicUrl}/${drawioFileName}`,
      alt: body.alt || `${body.title || 'Academic structure diagram'}: core article logic`,
      suggestedPlacement: body.suggestedPlacement || 'Place after the related method or results section',
    });
  } catch (error) {
    next(error);
  }
});

app.post('/render/carbon', async (req, res, next) => {
  try {
    const body = req.body || {};
    const baseName = safeSlug(body.articleSlug || body.title, 'code') + '-' + stableId(body);
    const svg = renderCarbonSvg(body);
    const assets = await writeAssetSet(baseName, svg);
    res.json({
      type: 'carbon',
      ...assets,
      alt: body.alt || ((body.title || 'Code image') + ': code preview'),
      suggestedPlacement: body.suggestedPlacement || 'Place after the related code explanation',
    });
  } catch (error) {
    next(error);
  }
});

app.post('/package', async (req, res, next) => {
  try {
    await ensureAssetsDir();
    const body = req.body || {};
    const articleTitle = body.articleTitle || body.title || 'Untitled Article';
    const articleSlug = safeSlug(body.articleSlug || articleTitle, 'article');
    const tasks = Array.isArray(body.tasks) ? body.tasks : [];
    const images = [];
    for (const task of tasks) {
      const endpoint = task.type === 'carbon' ? 'carbon' : 'drawio';
      const payload = { articleSlug, ...task };
      const svg = endpoint === 'carbon' ? renderCarbonSvg(payload) : renderDiagramSvg(payload);
      const baseName = articleSlug + '-' + endpoint + '-' + (images.length + 1) + '-' + stableId(payload);
      const assets = await writeAssetSet(baseName, svg);
      if (endpoint === 'drawio') {
        const drawioFileName = baseName + '.drawio';
        await fs.writeFile(path.join(assetsDir, drawioFileName), payload.drawioXml || makeDrawioXml(normalizeNodes(payload), payload.title), 'utf8');
        images.push({
          type: 'drawio',
          title: payload.title || ('Image ' + (images.length + 1)),
          ...assets,
          drawioUrl: assetsPublicUrl + '/' + drawioFileName,
          alt: payload.alt || ((payload.title || 'Academic structure diagram') + ': core article logic'),
          suggestedPlacement: payload.suggestedPlacement || 'Place after the related method or results section',
        });
      } else {
        images.push({
          type: 'carbon',
          title: payload.title || ('Image ' + (images.length + 1)),
          ...assets,
          alt: payload.alt || ((payload.title || 'Code image') + ': code preview'),
          suggestedPlacement: payload.suggestedPlacement || 'Place after the related code explanation',
        });
      }
    }
    const manifest = {
      articleTitle,
      articleSlug,
      assetsBaseUrl: assetsPublicUrl,
      generatedAt: new Date().toISOString(),
      images,
    };
    const manifestFileName = articleSlug + '-manifest-' + stableId(manifest) + '.json';
    await fs.writeFile(path.join(assetsDir, manifestFileName), JSON.stringify(manifest, null, 2), 'utf8');
    res.json({
      ...manifest,
      manifestUrl: assetsPublicUrl + '/' + manifestFileName,
    });
  } catch (error) {
    next(error);
  }
});

app.use((error, _req, res, _next) => {
  console.error(error);
  res.status(500).json({
    error: 'RENDER_FAILED',
    message: error.message,
  });
});

app.listen(port, () => {
  console.log(`renderer-api listening on ${port}`);
});
