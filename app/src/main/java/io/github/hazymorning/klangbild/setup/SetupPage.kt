package io.github.hazymorning.klangbild.setup

import io.github.hazymorning.klangbild.R
import io.github.hazymorning.klangbild.config.QuickActions
import io.github.hazymorning.klangbild.text.Texts
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The phone's setup page, served by the TV itself, in the language the TV shows. Self-contained
 * (no external resources) and as quiet as the app: one sans-serif face (the phone's own),
 * sentence case, warm near-black or paper as the phone is set.
 */
object SetupPage {
    fun html(t: Texts, prefilledCode: String?): String {
        val code = prefilledCode.orEmpty()
        val words = TEXTS.associate { (key, id) -> key to t[id] }
        // Words for the page's script, as one JSON object (escaped, so no text can end the script).
        val script = buildJsonObject { for ((key, id) in SCRIPT_TEXTS) put(key, JsonPrimitive(t[id])) }.toString().replace("</", "<\\/")
        var page = TEMPLATE
            .replace("__LANG__", t.locale.language)
            .replace("__CODE__", escape(code))
            .replace("__CODE_HIDDEN__", if (code.isNotEmpty()) "hidden" else "")
            .replace("__MAX_ACTIONS__", QuickActions.MAX.toString())
            .replace("__MAX_NAME__", QuickActions.MAX_NAME.toString())
            .replace("__SCRIPT_TEXTS__", script)
        for ((key, value) in words) page = page.replace("{{$key}}", escape(value))
        return page
    }

    /** [s] safe in HTML text and attribute values. */
    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    /** The page's fixed words: `{{key}}` in the template. */
    private val TEXTS = listOf(
        "title" to R.string.page_title,
        "subtitle" to R.string.page_subtitle,
        "codeHeading" to R.string.page_code_heading,
        "codeLabel" to R.string.page_code_label,
        "haHeading" to R.string.page_ha_heading,
        "address" to R.string.page_address,
        "signIn" to R.string.page_sign_in,
        "signInHint" to R.string.page_sign_in_hint,
        "tokenToggle" to R.string.page_token_toggle,
        "tokenLabel" to R.string.page_token_label,
        "tokenHint" to R.string.page_token_hint,
        "find" to R.string.page_find,
        "speakerHeading" to R.string.page_speaker_heading,
        "speakersEmpty" to R.string.page_speakers_empty,
        "entityLabel" to R.string.page_entity_label,
        "entityPlaceholder" to R.string.page_entity_placeholder,
        "generalHeading" to R.string.page_general_heading,
        "sleepBefore" to R.string.page_sleep_before,
        "sleepAfter" to R.string.page_sleep_after,
        "release" to R.string.page_release,
        "close" to R.string.page_close,
        "clock" to R.string.page_clock,
        "remote" to R.string.page_remote,
        "maHeading" to R.string.page_ma_heading,
        "maHint" to R.string.page_ma_hint,
        "maTokenPlaceholder" to R.string.page_ma_token_placeholder,
        "maTokenHint" to R.string.page_ma_token_hint,
        "lightsHeading" to R.string.page_lights_heading,
        "lightsHint" to R.string.page_lights_hint,
        "lightsEmpty" to R.string.page_lights_empty,
        "vistaHeading" to R.string.page_vista_heading,
        "vistaHint" to R.string.page_vista_hint,
        "weather" to R.string.page_weather,
        "weatherAuto" to R.string.page_weather_auto,
        "weatherNone" to R.string.page_weather_none,
        "actionsHeading" to R.string.page_actions_heading,
        "actionsHint" to R.string.page_actions_hint,
        "actionsEmpty" to R.string.page_actions_empty,
        "actionAdd" to R.string.page_action_add,
        "save" to R.string.page_save,
    )

    /** Words the page's script shows as it goes. */
    private val SCRIPT_TEXTS = listOf(
        "noSpeakers" to R.string.page_no_speakers,
        "noLights" to R.string.page_no_lights,
        "left" to R.string.page_light_left,
        "center" to R.string.page_light_center,
        "right" to R.string.page_light_right,
        "none" to R.string.page_none,
        "weatherAuto" to R.string.page_weather_auto,
        "weatherNone" to R.string.page_weather_none,
        "connecting" to R.string.page_connecting,
        "failed" to R.string.page_failed,
        "connected" to R.string.page_connected,
        "tvUnreachable" to R.string.page_tv_unreachable,
        "saved" to R.string.page_saved,
        "tokenKept" to R.string.page_token_kept,
        "signingIn" to R.string.page_signing_in,
        "signedIn" to R.string.page_signed_in,
        "signInFailed" to R.string.page_signin_failed,
        "withToken" to R.string.page_with_token,
        "noActions" to R.string.page_no_actions,
        "actionName" to R.string.page_action_name,
        "actionWhat" to R.string.page_action_what,
        "actionRemove" to R.string.page_action_remove,
        "groupScripts" to R.string.page_group_scripts,
        "groupScenes" to R.string.page_group_scenes,
        "groupAutomations" to R.string.page_group_automations,
        "groupButtons" to R.string.page_group_buttons,
        "groupSwitches" to R.string.page_group_switches,
        "groupLights" to R.string.page_group_lights,
    )

    private val TEMPLATE = """
<!doctype html>
<html lang="__LANG__">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="dark light">
<title>{{title}}</title>
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
  input[type=text], input[type=url], input[type=number], textarea, select {
    width: 100%; background: var(--card); color: var(--ink); border: 1px solid var(--line); border-radius: 10px;
    padding: 12px 14px; font: inherit; outline: none; }
  input:focus, textarea:focus { border-color: var(--accent); }
  textarea { min-height: 92px; resize: none; font-size: 14px; overflow-wrap: anywhere; }
  .hint { color: var(--faint); font-size: 15px; margin: 8px 0 0; }
  button { appearance: none; border: 1px solid var(--line); background: transparent; color: var(--ink);
    border-radius: 10px; padding: 12px 20px; font: 600 15px system-ui, sans-serif; margin-top: 18px; }
  button.primary { background: var(--accent); color: var(--on-accent); border-color: var(--accent); width: 100%; padding: 15px; font-size: 16px; }
  button:disabled { opacity: .45; }
  details { margin-top: 18px; }
  summary { list-style: none; cursor: pointer; color: var(--muted); font-size: 15px; text-decoration: underline;
    text-underline-offset: 3px; text-decoration-color: var(--line); }
  summary::-webkit-details-marker { display: none; }
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
  .status { min-height: 1.5em; margin-top: 16px; }
  .status.bad { color: var(--bad); } .status.good { color: var(--ink); }
  .action { border-top: 1px solid var(--line); margin-top: 18px; padding-top: 4px; }
  .action:first-child { border-top: 0; margin-top: 0; padding-top: 0; }
  button.quiet { border: 0; padding: 10px 0 0; margin-top: 6px; color: var(--muted); font-weight: 500; text-decoration: underline;
    text-underline-offset: 3px; text-decoration-color: var(--line); }
  .hidden { display: none; }
</style>
</head>
<body>
  <h1>Klangbild</h1>
  <p class="sub">{{subtitle}}</p>

  <section id="codeBox" class="__CODE_HIDDEN__">
    <h2>{{codeHeading}}</h2>
    <label for="code">{{codeLabel}}</label>
    <input id="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" value="__CODE__">
  </section>

  <section>
    <h2>{{haHeading}}</h2>
    <label for="url">{{address}}</label>
    <input id="url" type="url" inputmode="url" placeholder="http://192.168.1.10:8123" autocapitalize="off" autocorrect="off" spellcheck="false">
    <button id="signIn">{{signIn}}</button>
    <p class="hint">{{signInHint}}</p>
    <p class="status" id="haStatus"></p>
    <details id="tokenBox">
      <summary>{{tokenToggle}}</summary>
      <label for="token">{{tokenLabel}}</label>
      <textarea id="token" placeholder="eyJ0eXAiOiJKV1QiLCJhbGciOi…" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
      <p class="hint">{{tokenHint}}</p>
      <button id="find">{{find}}</button>
    </details>
  </section>

  <section>
    <h2>{{speakerHeading}}</h2>
    <div class="speakers" id="speakers"><p class="hint">{{speakersEmpty}}</p></div>
    <label for="entity">{{entityLabel}}</label>
    <input id="entity" type="text" placeholder="{{entityPlaceholder}}" autocapitalize="off" autocorrect="off" spellcheck="false">
  </section>

  <section>
    <h2>{{generalHeading}}</h2>
    <div class="inline">{{sleepBefore}} <input id="sleep" type="number" min="1" max="720" value="10"> {{sleepAfter}}</div>
    <label class="row"><input type="radio" name="action" value="release" checked> {{release}}</label>
    <label class="row"><input type="radio" name="action" value="close"> {{close}}</label>
    <label class="row"><input id="clock" type="checkbox" checked> {{clock}}</label>
    <label class="row"><input id="remote" type="checkbox" checked> {{remote}}</label>
  </section>

  <section>
    <h2>{{maHeading}}</h2>
    <p class="hint">{{maHint}}</p>
    <label for="maUrl">{{address}}</label>
    <input id="maUrl" type="url" inputmode="url" placeholder="http://192.168.1.10:8095" autocapitalize="off" autocorrect="off" spellcheck="false">
    <label for="maToken">{{tokenLabel}}</label>
    <textarea id="maToken" placeholder="{{maTokenPlaceholder}}" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
    <p class="hint">{{maTokenHint}}</p>
  </section>

  <section>
    <h2>{{lightsHeading}}</h2>
    <p class="hint">{{lightsHint}}</p>
    <div id="lights"><p class="hint">{{lightsEmpty}}</p></div>
  </section>

  <section>
    <h2>{{vistaHeading}}</h2>
    <p class="hint">{{vistaHint}}</p>
    <label for="weather">{{weather}}</label>
    <select id="weather"><option value="">{{weatherAuto}}</option><option value="none">{{weatherNone}}</option></select>
  </section>

  <section>
    <h2>{{actionsHeading}}</h2>
    <p class="hint">{{actionsHint}}</p>
    <div id="actions"><p class="hint">{{actionsEmpty}}</p></div>
    <button id="addAction" class="hidden">{{actionAdd}}</button>
  </section>

  <section>
    <button class="primary" id="save">{{save}}</button>
    <p class="status" id="status"></p>
  </section>

<script>
  const T = __SCRIPT_TEXTS__;
  const ${'$'} = (id) => document.getElementById(id);
  const params = new URLSearchParams(location.search);
  let hasToken = false;
  let lights = { lightsLeft: '', lightsCenter: '', lightsRight: '' };
  let lightsShown = false;
  let weather = '';
  let weatherShown = false;
  // The quick settings' own buttons: as stored, what Home Assistant offers for them, and whether the page has shown them.
  let actions = [];
  let actionOptions = [];
  let actionsShown = false;
  const MAX_ACTIONS = __MAX_ACTIONS__;
  const ACTION_GROUPS = [[['script'], T.groupScripts], [['scene'], T.groupScenes], [['automation'], T.groupAutomations],
    [['button', 'input_button'], T.groupButtons], [['switch', 'input_boolean'], T.groupSwitches], [['light'], T.groupLights]];
  // The code survives the trip to Home Assistant's sign-in page and back in this tab.
  if (!${'$'}('code').value) ${'$'}('code').value = sessionStorage.getItem('klangbild-code') || '';
  function code() { return ${'$'}('code').value.trim(); }
  function show(id, text, kind) { const s = ${'$'}(id); s.textContent = text || ''; s.className = 'status ' + (kind || ''); }
  function status(text, kind) { show('status', text, kind); }
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
    const box = ${'$'}('speakers'); box.textContent = '';
    if (!list.length) { const p = document.createElement('p'); p.className = 'hint'; p.textContent = T.noSpeakers; box.append(p); return; }
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
  function renderLights(list) {
    const box = ${'$'}('lights'); box.textContent = '';
    if (!list.length) { const p = document.createElement('p'); p.className = 'hint'; p.textContent = T.noLights; box.append(p); return; }
    [['lightsLeft', T.left], ['lightsCenter', T.center], ['lightsRight', T.right]].forEach(([key, label]) => {
      const l = document.createElement('label'); l.htmlFor = key; l.textContent = label;
      const sel = document.createElement('select'); sel.id = key;
      const none = document.createElement('option'); none.value = ''; none.textContent = T.none; sel.append(none);
      list.forEach((li) => {
        const o = document.createElement('option'); o.value = li.id; o.textContent = li.name + ' · ' + li.state;
        o.selected = li.id === lights[key]; sel.append(o);
      });
      box.append(l, sel);
    });
    lightsShown = true;
  }
  function renderWeather(list) {
    const sel = ${'$'}('weather'); sel.textContent = '';
    const auto = document.createElement('option'); auto.value = ''; auto.textContent = T.weatherAuto; sel.append(auto);
    list.forEach((w) => {
      const o = document.createElement('option'); o.value = w.id; o.textContent = w.name + ' · ' + w.state; sel.append(o);
    });
    const none = document.createElement('option'); none.value = 'none'; none.textContent = T.weatherNone; sel.append(none);
    sel.value = weather;
    if (sel.value !== weather) sel.value = '';
    weatherShown = true;
  }
  function optionName(id) { const o = actionOptions.find((a) => a.id === id); return o ? o.name : ''; }
  function actionSelect(selected) {
    const sel = document.createElement('select');
    const none = document.createElement('option'); none.value = ''; none.textContent = T.none; sel.append(none);
    ACTION_GROUPS.forEach(([domains, label]) => {
      const items = actionOptions.filter((o) => domains.includes(o.id.split('.')[0]));
      if (!items.length) return;
      const group = document.createElement('optgroup'); group.label = label;
      items.forEach((o) => {
        const opt = document.createElement('option'); opt.value = o.id; opt.textContent = o.name; opt.selected = o.id === selected; group.append(opt);
      });
      sel.append(group);
    });
    // One stored that Home Assistant no longer lists stays chosen.
    if (selected && sel.value !== selected) {
      const opt = document.createElement('option'); opt.value = selected; opt.textContent = selected; opt.selected = true; sel.append(opt);
    }
    return sel;
  }
  function renderActions() {
    const box = ${'$'}('actions'); box.textContent = '';
    actionsShown = true;
    if (!actionOptions.length && !actions.length) {
      const p = document.createElement('p'); p.className = 'hint'; p.textContent = T.noActions; box.append(p);
      ${'$'}('addAction').classList.add('hidden');
      return;
    }
    actions.forEach((a, i) => {
      const row = document.createElement('div'); row.className = 'action';
      const nameLabel = document.createElement('label'); nameLabel.htmlFor = 'actionName' + i; nameLabel.textContent = T.actionName;
      const name = document.createElement('input'); name.type = 'text'; name.id = 'actionName' + i; name.maxLength = __MAX_NAME__;
      name.value = a.name || ''; name.placeholder = optionName(a.entity);
      name.oninput = () => { a.name = name.value; };
      const whatLabel = document.createElement('label'); whatLabel.htmlFor = 'actionWhat' + i; whatLabel.textContent = T.actionWhat;
      const what = actionSelect(a.entity); what.id = 'actionWhat' + i;
      what.onchange = () => { a.entity = what.value; name.placeholder = optionName(what.value); };
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'quiet'; remove.textContent = T.actionRemove;
      remove.onclick = () => { actions.splice(i, 1); renderActions(); };
      row.append(nameLabel, name, whatLabel, what, remove); box.append(row);
    });
    ${'$'}('addAction').classList.toggle('hidden', actions.length >= MAX_ACTIONS);
  }
  function addAction() {
    if (actions.length >= MAX_ACTIONS) return;
    actions.push({ name: '', entity: '' });
    renderActions();
    const last = ${'$'}('actionWhat' + (actions.length - 1)); if (last) last.focus();
  }
  async function load() {
    if (code().length !== 6) return;
    sessionStorage.setItem('klangbild-code', code());
    const s = await api('/api/state');
    if (s.ok === false) { status(s.message, 'bad'); return; }
    ${'$'}('url').value = s.url || '';
    hasToken = s.auth === 'token';
    if (hasToken) ${'$'}('token').placeholder = T.tokenKept;
    ${'$'}('entity').value = s.entity || '';
    ${'$'}('sleep').value = s.sleepMinutes || 10;
    document.querySelectorAll('input[name=action]').forEach((r) => r.checked = (r.value === s.sleepAction));
    ${'$'}('clock').checked = !!s.clock; ${'$'}('remote').checked = !!s.remote;
    ${'$'}('maUrl').value = s.maUrl || '';
    lights = { lightsLeft: s.lightsLeft || '', lightsCenter: s.lightsCenter || '', lightsRight: s.lightsRight || '' };
    weather = s.weather || ''; ${'$'}('weather').value = weather;
    actions = (s.actions || []).map((a) => ({ name: a.name || '', entity: a.entity || '' }));
    if (s.hasMaToken) ${'$'}('maToken').placeholder = T.tokenKept;
    if (params.get('signin') === 'failed') show('haStatus', T.signInFailed, 'bad');
    else if (s.auth === 'signin') show('haStatus', T.signedIn, 'good');
    else if (hasToken) show('haStatus', T.withToken, 'good');
    if (s.url && s.auth !== 'none') find();
  }
  async function signIn() {
    ${'$'}('signIn').disabled = true;
    try {
      const r = await api('/api/signin', { url: ${'$'}('url').value });
      if (!r.ok) { show('haStatus', r.message || T.failed, 'bad'); return; }
      show('haStatus', T.signingIn, 'good');
      location.href = r.authorize;
    } catch (e) { show('haStatus', T.tvUnreachable, 'bad'); }
    finally { ${'$'}('signIn').disabled = false; }
  }
  async function find() {
    status(T.connecting);
    ${'$'}('find').disabled = true;
    try {
      const r = await api('/api/discover', { url: ${'$'}('url').value, token: ${'$'}('token').value });
      if (!r.ok) { status(r.message || T.failed, 'bad'); return; }
      ${'$'}('url').value = r.url;
      renderSpeakers(r.speakers, ${'$'}('entity').value);
      renderLights(r.lights || []);
      renderWeather(r.weather || []);
      actionOptions = r.actions || [];
      renderActions();
      status(T.connected, 'good');
    } catch (e) { status(T.tvUnreachable, 'bad'); }
    finally { ${'$'}('find').disabled = false; }
  }
  async function save() {
    ${'$'}('save').disabled = true;
    try {
      const action = document.querySelector('input[name=action]:checked').value;
      const body = {
        url: ${'$'}('url').value, token: ${'$'}('token').value, entity: ${'$'}('entity').value.trim(),
        sleepMinutes: parseInt(${'$'}('sleep').value, 10), sleepAction: action,
        clock: ${'$'}('clock').checked, remote: ${'$'}('remote').checked,
        maUrl: ${'$'}('maUrl').value, maToken: ${'$'}('maToken').value };
      // The weather once the page has listed it, or when it was chosen: a stored entity the page could not list stays.
      const w = ${'$'}('weather').value;
      if (weatherShown || w !== '' || weather === '') body.weather = w;
      // The lights only once the page has shown them; otherwise they stay as they are.
      if (lightsShown) ['lightsLeft', 'lightsCenter', 'lightsRight'].forEach((k) => body[k] = ${'$'}(k).value);
      // The buttons too, in their order; one without an entity is left out, one without a name takes the entity's.
      if (actionsShown) body.actions = actions.filter((a) => a.entity).map((a) => ({ name: a.name.trim() || optionName(a.entity), entity: a.entity }));
      const r = await api('/api/save', body);
      status(r.message || (r.ok ? T.saved : T.failed), r.ok ? 'good' : 'bad');
      if (r.ok) sessionStorage.removeItem('klangbild-code');
    } catch (e) { status(T.tvUnreachable, 'bad'); }
    finally { ${'$'}('save').disabled = false; }
  }
  ${'$'}('signIn').onclick = signIn;
  ${'$'}('find').onclick = find;
  ${'$'}('save').onclick = save;
  ${'$'}('addAction').onclick = addAction;
  ${'$'}('code').oninput = () => { if (code().length === 6) load(); };
  if (params.has('signin')) history.replaceState(null, '', '/');
  load();
</script>
</body>
</html>
""".trimIndent()
}
