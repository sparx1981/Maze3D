// usage: node shot.js scene.json out.png "query"
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const [,, scene, out, query = ''] = process.argv;
const root = __dirname;
const srv = http.createServer((req, res) => {
  const u = req.url.split('?')[0];
  const f = u === '/scene.json' ? scene : path.join(root, u === '/' ? 'preview.html' : u);
  fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); res.end(); } else { res.writeHead(200, { 'Content-Type': f.endsWith('.json') ? 'application/json' : 'text/html' }); res.end(d); } });
}).listen(0, async () => {
  const port = srv.address().port;
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--use-gl=angle'] });
  const qs = new URLSearchParams(query); const vw = +(qs.get('w') || 540), vh = +(qs.get('h') || 1080);
  const p = await b.newPage({ viewport: { width: vw, height: vh } });
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text().slice(0, 1500)); });
  await p.goto(`http://localhost:${port}/?scene=/scene.json&${query}`);
  await p.waitForFunction(() => document.title === 'ready' || document.title === 'error', null, { timeout: 120000 });
  console.log(await p.evaluate(() => window.__err || 'ok'));
  await p.screenshot({ path: out, omitBackground: qs.get('bg') === '0' });
  await b.close(); srv.close();
});
