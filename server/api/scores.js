// Daily leaderboard for Marblix. Stores only an arcade-style name and a time per day.
// Storage: any Redis with the Upstash REST API (Vercel Marketplace "Upstash Redis" sets KV_REST_API_URL / KV_REST_API_TOKEN).

const URL_ = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

async function redis(...cmd) {
  const r = await fetch(URL_, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd.map(String)),
  });
  if (!r.ok) throw new Error(`redis ${r.status}`);
  return (await r.json()).result;
}

function utcDay(offsetDays = 0) {
  return new Date(Date.now() + offsetDays * 86400000).toISOString().slice(0, 10);
}

module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  if (!URL_ || !TOKEN) return res.status(500).json({ error: 'storage not configured' });
  try {
    if (req.method === 'GET') {
      const day = String(req.query.day || utcDay());
      if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return res.status(400).json({ error: 'bad day' });
      const flat = (await redis('ZRANGE', `scores:${day}`, 0, 19, 'WITHSCORES')) || [];
      const scores = [];
      for (let i = 0; i + 1 < flat.length; i += 2) scores.push({ name: flat[i], ms: Math.round(Number(flat[i + 1])) });
      return res.status(200).json({ day, scores });
    }
    if (req.method === 'POST') {
      const { day, name, ms } = req.body || {};
      if (![utcDay(), utcDay(-1)].includes(day)) return res.status(400).json({ error: 'bad day' });
      if (typeof name !== 'string' || !/^[A-Z0-9]{3,8}$/.test(name)) return res.status(400).json({ error: 'bad name' });
      if (!Number.isInteger(ms) || ms < 4000 || ms > 3600000) return res.status(400).json({ error: 'bad time' });
      // LT: only ever improves a player's time for the day
      await redis('ZADD', `scores:${day}`, 'LT', ms, name);
      await redis('EXPIRE', `scores:${day}`, 3 * 86400);
      return res.status(200).json({ ok: true });
    }
    return res.status(405).json({ error: 'method not allowed' });
  } catch (e) {
    return res.status(502).json({ error: 'storage error' });
  }
};
