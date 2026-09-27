package com.himanshu.cache;

/**
 * Holds the dashboard's HTML/CSS/JS as an embedded Java text block, so the
 * page ships inside the compiled class itself rather than as a separate
 * classpath resource file.
 * <p>
 * This matters in practice: some IDEs' quick "Run" buttons compile just the
 * {@code .java} sources and skip copying {@code src/main/resources} the way
 * a full Maven build does. Embedding the page here means
 * {@link DashboardServer} always has something to serve — via a plain
 * {@code java} command, an IDE Run button, or {@code mvn exec:java} — with
 * no missing-resource failure mode possible.
 */
final class DashboardHtml {

    private DashboardHtml() {
    }

    static final String PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>LRU Cache — Live Dashboard</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600&display=swap" rel="stylesheet">
<style>
  :root {
    --bg: #101820;
    --panel: #172430;
    --line: #26333f;
    --text: #eaeef2;
    --muted: #7e8ca0;
    --amber: #e8a33d;
    --teal: #4fb8ae;
    --rust: #d9707a;
    --mono: 'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
    --sans: 'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', sans-serif;
  }

  * { box-sizing: border-box; }

  body {
    background: var(--bg);
    color: var(--text);
    font-family: var(--sans);
    margin: 0;
  }

  .wrap {
    max-width: 920px;
    margin: 0 auto;
    padding: 48px 24px 80px;
  }

  header .brand { display: flex; align-items: center; gap: 10px; }

  .dot {
    width: 8px; height: 8px; border-radius: 50%;
    background: var(--muted);
    box-shadow: 0 0 0 0 rgba(232, 163, 61, 0);
    transition: background .3s, box-shadow .3s;
  }
  .dot.live { background: var(--amber); box-shadow: 0 0 0 4px rgba(232, 163, 61, .15); }

  h1 { font-size: 1.5rem; font-weight: 600; margin: 0; letter-spacing: -0.01em; }

  .sub {
    color: var(--muted);
    font-family: var(--mono);
    font-size: .85rem;
    margin: 8px 0 0;
  }

  .hero { margin-top: 44px; }
  .hero-labels {
    display: flex; justify-content: space-between;
    font-size: .75rem; color: var(--muted);
    font-family: var(--mono);
    margin-bottom: 10px;
  }
  .cells { display: flex; gap: 6px; min-height: 64px; flex-wrap: wrap; }
  .cell {
    flex: 1 1 44px; max-width: 84px;
    background: var(--panel); border: 1px solid var(--line);
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    padding: 10px 4px; font-family: var(--mono);
    transition: border-color .25s ease, background .25s ease;
  }
  .cell.head {
    border-color: var(--amber);
    background: linear-gradient(180deg, rgba(232, 163, 61, .14), rgba(232, 163, 61, .02));
  }
  .cell .key { font-size: .95rem; font-weight: 600; }
  .cell .tag { font-size: .6rem; color: var(--muted); margin-top: 4px; height: .9em; }
  .cell.flash { animation: flash .6s ease; }
  @keyframes flash {
    0% { transform: scale(1.08); border-color: var(--teal); }
    100% { transform: scale(1); }
  }
  .hero-empty { color: var(--muted); font-family: var(--mono); font-size: .85rem; margin: 0; }

  .ledger {
    margin-top: 44px;
    display: flex;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
  }
  .ledger-item {
    flex: 1; padding: 18px 4px; text-align: center;
    border-left: 1px solid var(--line);
  }
  .ledger-item:first-child { border-left: none; }
  .ledger-value {
    display: block; font-family: var(--mono);
    font-size: 1.6rem; font-weight: 600;
  }
  .ledger-label {
    display: block; font-size: .75rem; color: var(--muted); margin-top: 4px;
  }

  .trend { margin-top: 44px; }
  .trend-head { display: flex; justify-content: space-between; align-items: baseline; }
  .trend-head h2 { font-size: .85rem; font-weight: 500; color: var(--muted); margin: 0; }
  #trendCurrent { font-family: var(--mono); color: var(--amber); }
  canvas { width: 100%; height: 72px; display: block; margin-top: 10px; }

  .panels {
    margin-top: 52px;
    display: grid; grid-template-columns: 1.1fr .9fr; gap: 32px;
  }
  @media (max-width: 720px) { .panels { grid-template-columns: 1fr; } }

  .panel h2 { font-size: .95rem; font-weight: 600; margin: 0 0 14px; }
  .row { display: flex; gap: 10px; align-items: end; margin-bottom: 12px; flex-wrap: wrap; }
  .row label { display: flex; flex-direction: column; font-size: .72rem; color: var(--muted); gap: 4px; }
  .row input {
    background: var(--panel); border: 1px solid var(--line); color: var(--text);
    padding: 8px 10px; font-family: var(--mono); font-size: .85rem; min-width: 110px;
  }

  button {
    background: var(--amber); border: none; color: #171008; font-weight: 600;
    padding: 9px 16px; cursor: pointer; font-family: var(--sans); font-size: .85rem;
  }
  button:hover { filter: brightness(1.06); }
  button.ghost { background: transparent; border: 1px solid var(--line); color: var(--text); margin-top: 4px; }
  button#trafficBtn { background: var(--teal); color: #08211d; }

  .result { font-family: var(--mono); font-size: .8rem; color: var(--muted); min-height: 1.2em; }
  .muted { color: var(--muted); font-size: .85rem; line-height: 1.55; margin: 0 0 16px; }
  .traffic-row { display: flex; align-items: center; gap: 12px; }
  .pill {
    font-family: var(--mono); font-size: .75rem; padding: 5px 10px;
    border: 1px solid var(--line); color: var(--muted);
  }
  .pill.on { color: var(--teal); border-color: var(--teal); }

  footer { margin-top: 60px; color: var(--muted); font-size: .75rem; line-height: 1.6; }
  footer code { font-family: var(--mono); }

  button:focus-visible, input:focus-visible { outline: 2px solid var(--amber); outline-offset: 2px; }

  @media (prefers-reduced-motion: reduce) {
    .dot, .cell { transition: none; }
    .cell.flash { animation: none; }
  }
</style>
</head>
<body>
  <div class="wrap">
    <header>
      <div class="brand">
        <span class="dot" id="statusDot"></span>
        <h1>LRU Cache Engine</h1>
      </div>
      <p class="sub">com.himanshu.cache — thread-safe, O(1) get/put — capacity <span id="capacityInline">–</span></p>
    </header>

    <section class="hero" aria-label="Current cache order">
      <div class="hero-labels">
        <span>newest access</span>
        <span>next eviction</span>
      </div>
      <div class="cells" id="cells"></div>
      <p class="hero-empty" id="heroEmpty">Cache is empty — put a key below to get started.</p>
    </section>

    <section class="ledger" aria-label="Live statistics">
      <div class="ledger-item">
        <span class="ledger-value" id="statHits">0</span>
        <span class="ledger-label">hits</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-value" id="statMisses">0</span>
        <span class="ledger-label">misses</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-value" id="statRate">0%</span>
        <span class="ledger-label">hit rate</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-value" id="statPuts">0</span>
        <span class="ledger-label">puts</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-value" id="statEvictions">0</span>
        <span class="ledger-label">evictions</span>
      </div>
    </section>

    <section class="trend" aria-label="Hit rate over time">
      <div class="trend-head">
        <h2>Hit rate, last 60 samples</h2>
        <span id="trendCurrent">—</span>
      </div>
      <canvas id="sparkline"></canvas>
    </section>

    <section class="panels">
      <div class="panel">
        <h2>Manual operations</h2>
        <form id="putForm" class="row">
          <label>key <input type="number" id="putKey" required></label>
          <label>value <input type="text" id="putValue" placeholder="optional"></label>
          <button type="submit">Put</button>
        </form>
        <form id="getForm" class="row">
          <label>key <input type="number" id="getKey" required></label>
          <button type="submit">Get</button>
        </form>
        <p class="result" id="opResult"></p>
        <button id="clearBtn" class="ghost">Clear cache</button>
      </div>

      <div class="panel">
        <h2>Simulated traffic</h2>
        <p class="muted">Three background threads issue random gets and puts so you can watch eviction happen under real concurrency.</p>
        <div class="traffic-row">
          <span class="pill" id="trafficPill">checking…</span>
          <button id="trafficBtn">Pause</button>
        </div>
      </div>
    </section>

    <footer>
      <p>Polling <code>/api/stats</code> every second. Capacity is fixed for this run — restart the app with a different value to change it.</p>
    </footer>
  </div>

<script>
  const state = { lastOrder: [], history: [] };

  async function fetchStats() {
    try {
      const res = await fetch('/api/stats', { cache: 'no-store' });
      const data = await res.json();
      render(data);
    } catch (e) {
      document.getElementById('statusDot').classList.remove('live');
    }
  }

  function render(data) {
    document.getElementById('statusDot').classList.add('live');
    document.getElementById('capacityInline').textContent = data.capacity;
    document.getElementById('statHits').textContent = data.hits;
    document.getElementById('statMisses').textContent = data.misses;
    document.getElementById('statRate').textContent = Math.round(data.hitRate * 100) + '%';
    document.getElementById('statPuts').textContent = data.puts;
    document.getElementById('statEvictions').textContent = data.evictions;

    renderCells(data.order);

    state.history.push(data.hitRate);
    if (state.history.length > 60) state.history.shift();
    drawSparkline();
    document.getElementById('trendCurrent').textContent = Math.round(data.hitRate * 100) + '%';

    const pill = document.getElementById('trafficPill');
    const btn = document.getElementById('trafficBtn');
    if (data.trafficRunning) {
      pill.textContent = 'running';
      pill.classList.add('on');
      btn.textContent = 'Pause';
    } else {
      pill.textContent = 'paused';
      pill.classList.remove('on');
      btn.textContent = 'Resume';
    }
  }

  function renderCells(order) {
    const container = document.getElementById('cells');
    const empty = document.getElementById('heroEmpty');
    empty.style.display = order.length ? 'none' : 'block';
    container.innerHTML = '';
    order.forEach((key, i) => {
      const cell = document.createElement('div');
      cell.className = 'cell' + (i === 0 ? ' head' : '');
      if (!state.lastOrder.includes(key)) cell.classList.add('flash');
      const tag = i === 0 ? 'head' : (i === order.length - 1 ? 'tail' : '');
      cell.innerHTML = '<span class="key">' + key + '</span><span class="tag">' + tag + '</span>';
      container.appendChild(cell);
    });
    state.lastOrder = order;
  }

  function drawSparkline() {
    const canvas = document.getElementById('sparkline');
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight || 72;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    const ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, w, h);

    ctx.strokeStyle = '#26333f';
    ctx.beginPath();
    ctx.moveTo(0, h - 1);
    ctx.lineTo(w, h - 1);
    ctx.stroke();

    const points = state.history;
    if (points.length < 2) return;
    ctx.strokeStyle = '#e8a33d';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    points.forEach((v, i) => {
      const x = (i / (points.length - 1)) * w;
      const y = h - (v * (h - 8)) - 4;
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    });
    ctx.stroke();
  }

  document.getElementById('putForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const key = document.getElementById('putKey').value;
    const value = document.getElementById('putValue').value;
    const res = await fetch('/api/put?key=' + encodeURIComponent(key) + '&value=' + encodeURIComponent(value));
    const data = await res.json();
    document.getElementById('opResult').textContent = res.ok ? ('put(' + key + ') ok') : (data.error || 'error');
    fetchStats();
  });

  document.getElementById('getForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const key = document.getElementById('getKey').value;
    const res = await fetch('/api/get?key=' + encodeURIComponent(key));
    const data = await res.json();
    document.getElementById('opResult').textContent = res.ok
      ? (data.value === null ? ('get(' + key + ') → miss') : ('get(' + key + ') → "' + data.value + '"'))
      : (data.error || 'error');
    fetchStats();
  });

  document.getElementById('clearBtn').addEventListener('click', async () => {
    await fetch('/api/clear');
    document.getElementById('opResult').textContent = 'cache cleared';
    fetchStats();
  });

  document.getElementById('trafficBtn').addEventListener('click', async () => {
    const running = document.getElementById('trafficPill').classList.contains('on');
    await fetch(running ? '/api/traffic/stop' : '/api/traffic/start');
    fetchStats();
  });

  fetchStats();
  setInterval(fetchStats, 1000);
  window.addEventListener('resize', drawSparkline);
</script>
</body>
</html>
""";
}
