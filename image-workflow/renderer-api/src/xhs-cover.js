import fs from 'node:fs/promises';
import satori from 'satori';
import sharp from 'sharp';

const WIDTH = 1242;
const HEIGHT = 1660;

function esc(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function units(text) {
  return Array.from(String(text || '')).reduce((total, char) => (
    total + (/[\u3400-\u9fff]/.test(char) ? 1 : /\s/.test(char) ? 0.28 : 0.55)
  ), 0);
}

export function safeCoverHeadline(value, fallback = '\u8bba\u6587\u7814\u7a76\u53d1\u73b0') {
  let text = String(value || fallback)
    .replace(/\s+/g, ' ')
    .replace(/(?:\u9996\u6b21|\u5168\u7403\u9996\u4e2a|\u5f7b\u5e95\u89e3\u51b3|\u5b8c\u5168\u51c6\u786e|\u767e\u5206\u4e4b\u767e|\u98a0\u8986\u6027|\u9769\u547d\u6027)/g, '')
    .trim();
  if (!text) text = fallback;
  return Array.from(text).slice(0, 44).join('');
}

function wrapHeadline(value, maxUnits = 14, maxLines = 3) {
  const chars = Array.from(safeCoverHeadline(value));
  const lines = [];
  let current = '';
  for (const char of chars) {
    if (units(current + char) > maxUnits && current) {
      lines.push(current.trim());
      current = char;
      if (lines.length === maxLines - 1) break;
    } else {
      current += char;
    }
  }
  if (current && lines.length < maxLines) lines.push(current.trim());
  const consumed = lines.join('').length;
  if (consumed < chars.join('').replace(/\s/g, '').length && lines.length) {
    lines[lines.length - 1] = `${Array.from(lines[lines.length - 1]).slice(0, -1).join('')}\u2026`;
  }
  return lines.filter(Boolean);
}

function safeColor(value, fallback) {
  return /^#[0-9a-fA-F]{6}$/.test(String(value || '')) ? String(value) : fallback;
}

function layoutForPreset(preset) {
  if (preset === 'image-focus') return { imageAreaHeight: 1240, panelHeight: 420, inset: 36 };
  if (preset === 'text-focus') return { imageAreaHeight: 900, panelHeight: 760, inset: 48 };
  return { imageAreaHeight: 1090, panelHeight: 570, inset: 48 };
}

function legacyCoverOverlaySvg({ title, label, showTitle = true, aiGenerated = false, sourceLabel = '', textColor = '#202a31', backgroundColor = '#ffffff', panelHeight = 570, imageAreaHeight = 1090 }) {
  if (!showTitle) {
    return Buffer.from(`<svg width="${WIDTH}" height="${HEIGHT}" xmlns="http://www.w3.org/2000/svg"/>`);
  }
  const footerText = sourceLabel || (aiGenerated
    ? '基于论文原文生成 · AI 图片已标记'
    : '论文原图 · 来源可追溯');
  const lines = wrapHeadline(title);
  const titleStart = imageAreaHeight + 230 - Math.max(0, lines.length - 2) * 50;
  const titleSpans = lines.map((line, index) => (
    `<tspan x="82" y="${titleStart + index * 88}">${esc(line)}</tspan>`
  )).join('');
  return Buffer.from(`
    <svg width="${WIDTH}" height="${HEIGHT}" xmlns="http://www.w3.org/2000/svg">
      <rect x="0" y="${imageAreaHeight}" width="${WIDTH}" height="${panelHeight}" fill="${safeColor(backgroundColor, '#ffffff')}" fill-opacity="0.96"/>
      <rect x="82" y="1160" width="74" height="8" fill="#2979a8"/>
      <text x="82" y="1195" font-family="Noto Sans CJK SC, Microsoft YaHei, sans-serif"
        font-size="30" font-weight="600" fill="#52606a">${esc(label || '\u8bba\u6587\u89e3\u8bfb')}</text>
      <text font-family="Noto Sans CJK SC, Microsoft YaHei, sans-serif"
        font-size="62" font-weight="700" fill="${safeColor(textColor, '#202a31')}">${titleSpans}</text>
      <text x="82" y="1590" font-family="Noto Sans CJK SC, Microsoft YaHei, sans-serif"
        font-size="25" fill="#74808a">${esc(footerText)}</text>
    </svg>
  `);
}

const fontDataPromise = fs.readFile(new URL('./fonts/NotoSansCJKsc-Regular.otf', import.meta.url));

function element(type, style, children, props = {}) {
  return { type, props: { ...props, style, children } };
}

async function coverOverlaySvg(options = {}) {
  const { title, label, showTitle = true, aiGenerated = false, sourceLabel = '' } = options;
  const layout = layoutForPreset(options.templatePreset);
  const textColor = safeColor(options.textColor, '#202a31');
  const backgroundColor = safeColor(options.backgroundColor, '#ffffff');
  if (!showTitle) {
    return Buffer.from(`<svg width="${WIDTH}" height="${HEIGHT}" xmlns="http://www.w3.org/2000/svg"/>`);
  }
  try {
    const fontData = await fontDataPromise;
    const footerText = sourceLabel || (aiGenerated
      ? '基于论文原文生成 · AI 图片已标记'
      : '论文原图 · 来源可追溯');
    const headline = wrapHeadline(title).join('\n');
    const panel = element('div', {
      width: '100%', height: layout.panelHeight, display: 'flex', flexDirection: 'column',
      padding: '66px 82px 56px', backgroundColor,
      color: textColor,
    }, [
      element('div', { width: 74, height: 8, backgroundColor: '#2979a8', marginBottom: 18 }, ''),
      element('div', { display: 'flex', fontSize: 30, color: '#52606a', marginBottom: 24 }, label || '论文解读'),
      element('div', {
        display: 'flex', whiteSpace: 'pre-wrap', fontSize: 62, fontWeight: 700,
        lineHeight: 1.25, color: textColor, flexGrow: 1,
      }, headline),
      element('div', { display: 'flex', fontSize: 25, color: '#74808a' }, footerText),
    ]);
    const root = element('div', {
      width: '100%', height: '100%', display: 'flex', flexDirection: 'column',
      justifyContent: 'flex-end', fontFamily: 'Noto Sans CJK SC',
    }, panel, { lang: 'zh-CN' });
    const svg = await satori(root, {
      width: WIDTH,
      height: HEIGHT,
      fonts: [
        { name: 'Noto Sans CJK SC', data: fontData, weight: 400, style: 'normal' },
        { name: 'Noto Sans CJK SC', data: fontData, weight: 700, style: 'normal' },
      ],
      pointScaleFactor: 2,
    });
    return Buffer.from(svg);
  } catch {
    return legacyCoverOverlaySvg(options);
  }
}
export async function renderXhsCover(baseBuffer, options = {}) {
  const layout = layoutForPreset(options.templatePreset);
  const imageAreaHeight = layout.imageAreaHeight;
  const inset = layout.inset;
  const rotated = await sharp(baseBuffer).rotate().toBuffer();
  const backdrop = options.backgroundMode === 'solid'
    ? await sharp({ create: { width: WIDTH, height: imageAreaHeight, channels: 3, background: safeColor(options.backgroundColor, '#eef2f4') } }).jpeg({ quality: 90 }).toBuffer()
    : await sharp(rotated)
    .resize(WIDTH, imageAreaHeight, { fit: 'cover', position: 'attention' })
    .blur(24)
    .modulate({ brightness: 0.78, saturation: 0.72 })
    .jpeg({ quality: 84, mozjpeg: true })
    .toBuffer();
  const foreground = await sharp(rotated)
    .resize(WIDTH - inset * 2, imageAreaHeight - inset * 2, {
      fit: 'contain',
      // Match the editable template color instead of baking in a second
      // light-gray backing layer around the source image.
      background: safeColor(options.backgroundColor, '#eef2f4'),
    })
    .jpeg({ quality: 94, mozjpeg: true })
    .toBuffer();
  const canvas = await sharp({
    create: {
      width: WIDTH,
      height: HEIGHT,
      channels: 3,
      background: safeColor(options.backgroundColor, '#eef2f4'),
    },
  })
    .composite([
      { input: backdrop, top: 0, left: 0 },
      { input: foreground, top: inset, left: inset },
    ])
    .jpeg({ quality: 92, mozjpeg: true })
    .toBuffer();
  return sharp(canvas)
    .composite([{ input: await coverOverlaySvg(options), top: 0, left: 0 }])
    .jpeg({ quality: 91, mozjpeg: true })
    .toBuffer();
}

export async function renderFallbackBackground(options = {}) {
  const anchors = (Array.isArray(options.anchors) ? options.anchors : [])
    .slice(0, 3)
    .map((anchor, index) => (
      `<text x="92" y="${620 + index * 98}" font-family="Noto Sans CJK SC, Microsoft YaHei, sans-serif"
        font-size="42" font-weight="600" fill="#35434c">${index + 1}. ${esc(String(anchor).slice(0, 36))}</text>`
    )).join('');
  return sharp(Buffer.from(`
    <svg width="${WIDTH}" height="${HEIGHT}" xmlns="http://www.w3.org/2000/svg">
      <rect width="${WIDTH}" height="${HEIGHT}" fill="#eef3f5"/>
      <rect x="70" y="110" width="1102" height="900" fill="#ffffff"/>
      <circle cx="190" cy="275" r="64" fill="#2979a8"/>
      <path d="M310 275 H1000" stroke="#a8bac4" stroke-width="8"/>
      <path d="M210 275 L470 520 L730 360 L1010 670" fill="none" stroke="#6c9a86" stroke-width="18"/>
      ${anchors}
    </svg>
  `)).jpeg({ quality: 92 }).toBuffer();
}

export const XHS_COVER_SIZE = { width: WIDTH, height: HEIGHT };
