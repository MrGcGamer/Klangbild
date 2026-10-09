package io.github.hazymorning.klangbild.setup

/**
 * The phone-side setup page, served by the TV itself. Self-contained (no external
 * resources), German, and as quiet as the app: one sans-serif face (the phone's own),
 * sentence case, warm near-black or paper as the phone is set.
 */
object SetupPage {
    fun html(prefilledCode: String?): String {
        val code = prefilledCode ?: ""
        return TEMPLATE.replace("__CODE__", code).replace("__CODE_HIDDEN__", if (code.isNotEmpty()) "hidden" else "")
    }

    private val TEMPLATE = """
<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="dark light">
<title>Klangbild einrichten</title>
<style>
  :root {
    --wall: #0f0d0c; --card: #171412; --line: #2c2723; --ink: #ebe3d8; --muted: #b3a89b;
    --faint: #8a8076; --accent: #ebe3d8; --on-accent: #141110; --bad: #e0876f;
  }
  @media (prefers-color-scheme: light) {
    :root {
      --wall: #ebe6df; --card: #f6f2ec; --line: #d6cec4; --ink: #2b2622; --muted: #5e554d;
      --faint: #7a7067; --accent: #2b2622; --on-accent: #f6f2ec; --bad: #a8442e;
    }
  }
  * { box-sizing: border-box; }
  html, body { margin: 0; background: var(--wall); color: var(--ink); }
  body { font: 17px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
         padding: max(28px, env(safe-area-inset-top)) 22px 48px; max-width: 560px; margin: 0 auto; }
  h1 { font-size: 36px; font-weight: 500; line-height: 1.1; margin: 8px 0 4px; letter-spacing: -0.01em; }
  .sub { color: var(--muted); margin: 0 0 28px; }
  section { border-top: 1px solid var(--line); padding: 22px 0 8px; }
  h2 { font-size: 17px; font-weight: 600; color: var(--ink); margin: 0 0 6px; }
  label { display: block; color: var(--muted); font-size: 15px; margin: 14px 0 6px; }
  input[type=text], input[type=url], input[type=number], textarea {
    width: 100%; background: var(--card); color: var(--ink); border: 1px solid var(--line); border-radius: 10px;
    padding: 12px 14px; font: inherit; outline: none; }
  input:focus, textarea:focus { border-color: var(--accent); }
  textarea { min-height: 92px; resize: none; font-size: 14px; overflow-wrap: anywhere; }
  .hint { color: var(--faint); font-size: 15px; margin: 8px 0 0; }
  button { appearance: none; border: 1px solid var(--line); background: transparent; color: var(--ink);
    border-radius: 10px; padding: 12px 20px; font: 600 15px system-ui, sans-serif; margin-top: 18px; }
  button.primary { background: var(--accent); color: var(--on-accent); border-color: var(--accent); width: 100%; padding: 15px; font-size: 16px; }
  button:disabled { opacity: .45; }
  .speakers { display: grid; gap: 10px; }
  .speaker { display: flex; gap: 12px; align-items: center; background: var(--card); border: 1px solid var(--line);
    border-radius: 12px; padding: 12px 14px; margin: 0; color: var(--ink); }
  .speaker:has(input:checked) { border-color: var(--accent); }
  .speaker input { accent-color: var(--accent); width: 20px; height: 20px; }
  .speaker .name { font-size: 17px; }
  .speaker .meta { color: var(--faint); font-size: 14px; }
  .row { display: flex; gap: 12px; align-items: center; margin: 12px 0; color: var(--muted); }
  .row input[type=checkbox], .row input[type=radio] { accent-color: var(--accent); width: 20px; height: 20px; flex: none; }
  .inline { display: flex; gap: 10px; align-items: center; color: var(--muted); }
  .inline input { width: 90px; }
  #status { min-height: 1.5em; margin-top: 16px; }
  #status.bad { color: var(--bad); } #status.good { color: var(--ink); }
  .hidden { display: none; }
</style>
</head>
<body>
  <h1>Klangbild</h1>
  <p class="sub">Einrichtung für den Fernseher</p>

  <section id="codeBox" class="__CODE_HIDDEN__">
    <h2>Code</h2>
    <label for="code">Der sechsstellige Code auf dem Fernseher</label>
    <input id="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" value="__CODE__">
  </section>

  <section>
    <h2>1 · Home Assistant</h2>
    <label for="url">Adresse</label>
    <input id="url" type="url" inputmode="url" placeholder="http://192.168.1.10:8123" autocapitalize="off" autocorrect="off" spellcheck="false">
    <label for="token">Langlebiges Zugangstoken</label>
    <textarea id="token" placeholder="eyJ0eXAiOiJKV1QiLCJhbGciOi…" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
    <p class="hint" id="tokenHint">In Home Assistant: Profil → Sicherheit → „Langlebige Zugangstoken“ → Token erstellen.</p>
    <button id="find">Lautsprecher suchen</button>
  </section>

  <section>
    <h2>2 · Lautsprecher</h2>
    <div class="speakers" id="speakers"><p class="hint">Erst Home Assistant verbinden – dann erscheinen hier die Lautsprecher.</p></div>
    <label for="entity">oder Entität direkt eingeben</label>
    <input id="entity" type="text" placeholder="media_player.wohnzimmer" autocapitalize="off" autocorrect="off" spellcheck="false">
  </section>

  <section>
    <h2>3 · Ruhe</h2>
    <div class="inline">Nach <input id="sleep" type="number" min="1" max="720" value="10"> Minuten ohne Musik</div>
    <label class="row"><input type="radio" name="action" value="release" checked> den Bildschirm freigeben (Fernseher darf schlafen)</label>
    <label class="row"><input type="radio" name="action" value="close"> die App schließen</label>
    <label class="row"><input id="clock" type="checkbox" checked> Uhr anzeigen</label>
    <label class="row"><input id="dayNight" type="checkbox" checked> abends wärmer und dunkler, nachts sehr zurückhaltend</label>
    <label class="row"><input id="remote" type="checkbox" checked> Fernbedienung steuert die Musik (OK: Pause, ◀ ▶: Titel)</label>
  </section>

  <section>
    <h2>4 · Music Assistant (freiwillig)</h2>
    <p class="hint">Für Cover Flow: Mit Music Assistant zeigt Klangbild die ganze Warteschlange – was lief und was kommt.</p>
    <label for="maUrl">Adresse</label>
    <input id="maUrl" type="url" inputmode="url" placeholder="http://192.168.1.10:8095" autocapitalize="off" autocorrect="off" spellcheck="false">
    <label for="maToken">Langlebiges Zugangstoken</label>
    <textarea id="maToken" placeholder="Aus Music Assistant" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
    <p class="hint">In Music Assistant unter Einstellungen → Profil ein langlebiges Zugangstoken erstellen. Leer lassen, wenn du es nicht brauchst.</p>
  </section>

  <section>
    <button class="primary" id="save">Speichern</button>
    <p id="status"></p>
  </section>

<script>
  const ${'$'} = (id) => document.getElementById(id);
  let hasToken = false;
  function code() { return ${'$'}('code').value.trim(); }
  function status(text, kind) { const s = ${'$'}('status'); s.textContent = text || ''; s.className = kind || ''; }
  async function api(path, body) {
    const res = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Object.assign({ code: code() }, body || {})) });
    let data = {};
    try { data = await res.json(); } catch (e) {}
    if (!res.ok && data.ok === undefined) data.ok = false;
    if (res.status === 403) { ${'$'}('codeBox').classList.remove('hidden'); }
    return data;
  }
  function renderSpeakers(list, selected) {
    const box = ${'$'}('speakers'); box.innerHTML = '';
    if (!list.length) { box.innerHTML = '<p class="hint">Keine media_player-Entitäten gefunden.</p>'; return; }
    list.forEach((sp) => {
      const row = document.createElement('label'); row.className = 'speaker';
      const r = document.createElement('input'); r.type = 'radio'; r.name = 'speaker'; r.value = sp.id;
      r.checked = sp.id === selected; r.onchange = () => { ${'$'}('entity').value = sp.id; };
      const text = document.createElement('div');
      const name = document.createElement('div'); name.className = 'name'; name.textContent = sp.name;
      const meta = document.createElement('div'); meta.className = 'meta';
      meta.textContent = sp.id + (sp.ma ? ' · Music Assistant' : '') + ' · ' + sp.state;
      text.append(name, meta); row.append(r, text); box.append(row);
    });
  }
  async function load() {
    if (code().length !== 6) return;
    const s = await api('/api/state');
    if (s.ok === false) { status(s.message, 'bad'); return; }
    ${'$'}('url').value = s.url || '';
    hasToken = !!s.hasToken;
    if (hasToken) ${'$'}('token').placeholder = 'Gespeichertes Token bleibt erhalten – nur zum Ändern neu einfügen';
    ${'$'}('entity').value = s.entity || '';
    ${'$'}('sleep').value = s.sleepMinutes || 10;
    document.querySelectorAll('input[name=action]').forEach((r) => r.checked = (r.value === s.sleepAction));
    ${'$'}('clock').checked = !!s.clock; ${'$'}('dayNight').checked = !!s.dayNight; ${'$'}('remote').checked = !!s.remote;
    ${'$'}('maUrl').value = s.maUrl || '';
    if (s.hasMaToken) ${'$'}('maToken').placeholder = 'Gespeichertes Token bleibt erhalten – nur zum Ändern neu einfügen';
    if (s.url && hasToken) find();
  }
  async function find() {
    status('Verbinde mit Home Assistant …');
    ${'$'}('find').disabled = true;
    try {
      const r = await api('/api/discover', { url: ${'$'}('url').value, token: ${'$'}('token').value });
      if (!r.ok) { status(r.message || 'Das hat nicht geklappt.', 'bad'); return; }
      ${'$'}('url').value = r.url;
      renderSpeakers(r.speakers, ${'$'}('entity').value);
      status('Verbunden. Bitte den Lautsprecher wählen.', 'good');
    } catch (e) { status('Der Fernseher ist nicht erreichbar.', 'bad'); }
    finally { ${'$'}('find').disabled = false; }
  }
  async function save() {
    ${'$'}('save').disabled = true;
    try {
      const action = document.querySelector('input[name=action]:checked').value;
      const r = await api('/api/save', {
        url: ${'$'}('url').value, token: ${'$'}('token').value, entity: ${'$'}('entity').value.trim(),
        sleepMinutes: parseInt(${'$'}('sleep').value, 10), sleepAction: action,
        clock: ${'$'}('clock').checked, dayNight: ${'$'}('dayNight').checked, remote: ${'$'}('remote').checked,
        maUrl: ${'$'}('maUrl').value, maToken: ${'$'}('maToken').value });
      status(r.message || (r.ok ? 'Gespeichert.' : 'Das hat nicht geklappt.'), r.ok ? 'good' : 'bad');
    } catch (e) { status('Der Fernseher ist nicht erreichbar.', 'bad'); }
    finally { ${'$'}('save').disabled = false; }
  }
  ${'$'}('find').onclick = find;
  ${'$'}('save').onclick = save;
  ${'$'}('code').oninput = () => { if (code().length === 6) load(); };
  load();
</script>
</body>
</html>
""".trimIndent()
}
