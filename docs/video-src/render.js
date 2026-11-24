const { chromium } = require('playwright');
const [,, html, outDir, fps, dur, only] = process.argv;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await p.goto('file://' + html);
  await p.evaluate(() => document.fonts.ready);
  const times = only ? only.split(',').map(Number) : [...Array(Math.round(+dur * +fps)).keys()].map(i => i / +fps);
  let n = 0;
  for (const t of times) {
    await p.evaluate(t => window.render(t), t);
    await p.locator('#stage').screenshot({ path: `${outDir}/f${String(n++).padStart(5, '0')}.png` });
  }
  await b.close();
})();
