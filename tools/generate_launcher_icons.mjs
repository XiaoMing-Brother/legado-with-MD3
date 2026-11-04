#!/usr/bin/env bun
import sharp from "sharp";
import { mkdir, writeFile } from "fs/promises";
import path from "path";

const ROOT = path.resolve(import.meta.dir, "..");
const SRC_DIR = path.join(ROOT, "tools/icon-sources");
const RES = path.join(ROOT, "app/src/main/res");
const FG_SIZE = 432;
const DENSITIES = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
const ICONS = {
  apricot: { file: "apricot.png", fg: "ic_launcher_fg_apricot", bg: "ic_launcher_bg_apricot" },
  // 暗夜门图标边缘是门框高光，采样值偏亮，用深色底避免出现浅灰光环。
  night: { file: "night.png", fg: "ic_launcher_fg_night", bg: "ic_launcher_bg_night", bgOverride: "#14161C" },
  dreambook: { file: "dreambook.png", fg: "ic_launcher_fg_dreambook", bg: "ic_launcher_bg_dreambook" },
  dreampage: { file: "dreampage.png", fg: "ic_launcher_fg_dreampage", bg: "ic_launcher_bg_dreampage" },
};

function rgbToHex(r, g, b) {
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`.toUpperCase();
}

function averageEdgeColor(data, width, height) {
  const edge = [];
  for (let x = 0; x < width; x++) edge.push([x, 0], [x, height - 1]);
  for (let y = 1; y < height - 1; y++) edge.push([0, y], [width - 1, y]);
  const pick = (skipNearWhite) => {
    let r = 0;
    let g = 0;
    let b = 0;
    let count = 0;
    for (const [x, y] of edge) {
      const i = (y * width + x) * 4;
      if (data[i + 3] < 16) continue;
      const pr = data[i];
      const pg = data[i + 1];
      const pb = data[i + 2];
      if (skipNearWhite && pr > 245 && pg > 245 && pb > 245) continue;
      r += pr;
      g += pg;
      b += pb;
      count++;
    }
    if (!count) return null;
    return { r: Math.round(r / count), g: Math.round(g / count), b: Math.round(b / count) };
  };
  return pick(true) ?? pick(false) ?? { r: 255, g: 255, b: 255 };
}

async function loadTrimmedSquare(inputPath) {
  const trimmed = await sharp(inputPath).trim({ threshold: 12 }).png().toBuffer();
  const meta = await sharp(trimmed).metadata();
  const side = Math.max(meta.width, meta.height);
  const padX = Math.floor((side - meta.width) / 2);
  const padY = Math.floor((side - meta.height) / 2);
  const squared = await sharp(trimmed)
    .extend({
      top: padY,
      bottom: side - meta.height - padY,
      left: padX,
      right: side - meta.width - padX,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    })
    .png()
    .toBuffer();
  const { data, info } = await sharp(trimmed).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const { r, g, b } = averageEdgeColor(data, info.width, info.height);
  const bgHex = rgbToHex(r, g, b);
  const foreground = await sharp(squared)
    .resize(FG_SIZE, FG_SIZE, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .webp({ quality: 95 })
    .toBuffer();
  const preview512 = await sharp(squared)
    .resize(512, 512, { fit: "contain", background: { r, g, b, alpha: 255 } })
    .png()
    .toBuffer();
  return { foreground, preview512, bgHex };
}

async function compositeLegacyIcon(foregroundPng, size, bg) {
  const fg = await sharp(foregroundPng)
    .resize(size, size, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  return sharp({ create: { width: size, height: size, channels: 4, background: { ...bg, alpha: 255 } } })
    .composite([{ input: fg, gravity: "center" }])
    .webp({ quality: 92 })
    .toBuffer();
}

async function main() {
  const drawableDir = path.join(RES, "drawable");
  await mkdir(drawableDir, { recursive: true });
  const processed = {};
  for (const [key, cfg] of Object.entries(ICONS)) {
    const { foreground, preview512, bgHex } = await loadTrimmedSquare(path.join(SRC_DIR, cfg.file));
    processed[key] = { ...cfg, bgHex: cfg.bgOverride ?? bgHex, foreground, preview512 };
    await writeFile(path.join(drawableDir, `${cfg.fg}.webp`), foreground);
    console.log(key, processed[key].bgHex);
  }
  const apricot = processed.apricot;
  const bg = {
    r: parseInt(apricot.bgHex.slice(1, 3), 16),
    g: parseInt(apricot.bgHex.slice(3, 5), 16),
    b: parseInt(apricot.bgHex.slice(5, 7), 16),
  };
  const fgPng = await sharp(apricot.foreground).png().toBuffer();
  for (const [folder, size] of Object.entries(DENSITIES)) {
    const dir = path.join(RES, `mipmap-${folder}`);
    await mkdir(dir, { recursive: true });
    const legacy = await compositeLegacyIcon(fgPng, size, bg);
    await writeFile(path.join(dir, "ic_launcher.webp"), legacy);
    await writeFile(path.join(dir, "ic_launcher_round.webp"), legacy);
  }
  await writeFile(path.join(drawableDir, "ic_launcher.webp"), await compositeLegacyIcon(fgPng, 192, bg));
  await writeFile(path.join(ROOT, "app/src/main/ic_launcher-web.png"), apricot.preview512);
  let colorsXml = await Bun.file(path.join(RES, "values/colors.xml")).text();
  for (const p of Object.values(processed)) {
    const line = `    <color name="${p.bg}">${p.bgHex}</color>`;
    const re = new RegExp(`    <color name="${p.bg}">[^<]+</color>\n?`);
    colorsXml = re.test(colorsXml)
      ? colorsXml.replace(re, `${line}\n`)
      : colorsXml.replace(`    <color name="ic_launcher_background_w">`, `${line}\n    <color name="ic_launcher_background_w">`);
  }
  colorsXml = colorsXml.replace(
    /<color name="ic_launcher_background">[^<]+<\/color>/,
    `<color name="ic_launcher_background">${apricot.bgHex}</color>`,
  );
  colorsXml = colorsXml.replace(
    /<color name="ic_launcher_background_w">[^<]+<\/color>/,
    `<color name="ic_launcher_background_w">${processed.dreampage.bgHex}</color>`,
  );
  await writeFile(path.join(RES, "values/colors.xml"), colorsXml);
  console.log("Done");
}

await main();
