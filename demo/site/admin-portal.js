// Portal administrativo. Todo texto vindo da API é inserido com textContent, nunca como HTML.
const SESSION_STORAGE = "ia-admin-session-token";
const CLIENT_KEY_STORAGE = "ia-demo-api-key";
const $ = (selector) => document.querySelector(selector);

const state = {
  token: readStored(SESSION_STORAGE),
  user: null,
  createdKey: "",
  clients: [],
  // Totais dos últimos 30 dias por id de cliente
  usage: new Map(),
  // Cotação do dólar em reais ({ value, at }); nula se a consulta falhar
  rate: null,
};

const RATE_URL = "https://economia.awesomeapi.com.br/json/last/USD-BRL";

const EMPTY_TOTALS = { calls: 0, successfulCalls: 0, cacheHits: 0, promptTokens: 0, outputTokens: 0, estimatedCost: 0 };
const number = new Intl.NumberFormat("pt-BR");
const percent = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 1 });
// Os custos por cliente costumam ficar em frações de centavo
const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 6 });
const reais = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", minimumFractionDigits: 2, maximumFractionDigits: 6 });
const rateFormat = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", minimumFractionDigits: 4 });
const dateFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" });
const dateTimeFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

// Falha na cotação não impede o portal: os custos ficam em dólar
async function loadRate() {
  try {
    const response = await fetch(RATE_URL);
    if (!response.ok) return null;
    const { USDBRL } = await response.json();
    const value = Number(USDBRL.bid);
    return value > 0 ? { value, at: new Date(Number(USDBRL.timestamp) * 1000) } : null;
  } catch {
    return null;
  }
}

/** Custo principal em reais (ou dólar sem cotação) e linha secundária com o valor em dólar. */
function cost(usd) {
  if (!state.rate) return { main: money.format(usd), detail: "Cotação do dólar indisponível no momento" };
  return { main: reais.format(usd * state.rate.value), detail: `${money.format(usd)} · ${rateLabel()}` };
}

const rateLabel = () =>
  `US$ 1 = ${rateFormat.format(state.rate.value)} em ${dateTimeFormat.format(state.rate.at)}`;

function readStored(name) {
  try { return sessionStorage.getItem(name) ?? ""; } catch { return ""; }
}

function store(name, value) {
  try {
    if (value) sessionStorage.setItem(name, value);
    else sessionStorage.removeItem(name);
  } catch {
    // Sem sessionStorage, a sessão vale apenas até recarregar a página.
  }
}

function el(tag, attributes = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (name === "class") node.className = value;
    else if (name === "text") node.textContent = value;
    else node.setAttribute(name, value);
  }
  for (const child of children.flat()) if (child != null) node.append(child);
  return node;
}

function icon(path) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  const shape = document.createElementNS("http://www.w3.org/2000/svg", "path");
  shape.setAttribute("d", path);
  svg.append(shape);
  return svg;
}

const alertBox = () => (state.user && !state.user.mustChangePassword ? $("#alert") : $("#login-alert"));

function showError(message) {
  const alert = alertBox();
  alert.textContent = message;
  alert.hidden = false;
}

function clearError() {
  $("#alert").hidden = true;
  $("#login-alert").hidden = true;
}

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function toError(response) {
  let detail = `Erro ${response.status}`;
  try {
    const problem = await response.json();
    detail = problem.detail ?? detail;
    if (problem.errors && !Array.isArray(problem.errors)) {
      detail += " " + Object.entries(problem.errors).map(([field, msg]) => `${field}: ${msg}`).join("; ");
    }
  } catch {
    // Corpo não é JSON.
  }
  return new ApiError(detail, response.status);
}

async function request(path, options = {}, authenticated = true) {
  if (authenticated && !state.token) throw new ApiError("Entre no portal administrativo.", 401);
  const headers = { ...(options.headers ?? {}) };
  if (authenticated) headers.Authorization = `Bearer ${state.token}`;
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    options = { ...options, body: JSON.stringify(options.json) };
  }
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) throw await toError(response);
  return response;
}

const api = (path, options) => request(path, options, true);

async function run(button, action) {
  clearError();
  if (button) button.disabled = true;
  try { await action(); }
  catch (error) {
    if (error instanceof ApiError && error.status === 401 && state.user) clearSession();
    showError(error instanceof ApiError ? error.message : `Falha de comunicação: ${error.message}`);
  }
  finally { if (button) button.disabled = false; }
}

// ---------- sessão ----------

function setSession(session) {
  state.token = session.token;
  state.user = session.user;
  store(SESSION_STORAGE, state.token);
  renderAccess();
}

function clearSession() {
  state.token = "";
  state.user = null;
  state.clients = [];
  state.usage = new Map();
  store(SESSION_STORAGE, "");
  renderAccess();
}

function renderAccess() {
  const authenticated = Boolean(state.user);
  const portal = authenticated && !state.user.mustChangePassword;
  $("#login-view").hidden = portal;
  $("#app-view").hidden = !portal;
  $("#login-form").hidden = authenticated;
  $("#password-form").hidden = !authenticated || portal;
  if (authenticated) {
    $("#admin-email-label").textContent = state.user.email;
    $("#admin-role-label").textContent = state.user.role === "ADMIN" ? "Administrador" : state.user.role;
    $("#admin-avatar").textContent = state.user.email.charAt(0);
  }
  if (portal) route();
}

async function loadCurrentSession() {
  if (!state.token) return renderAccess();
  try {
    state.user = await (await api("/v1/admin/auth/me")).json();
  } catch {
    return clearSession();
  }
  renderAccess();
  if (!state.user.mustChangePassword) await run(null, loadData);
}

// ---------- dados ----------

async function loadData() {
  const [clients, usage, rate] = await Promise.all([
    api("/v1/admin/clients").then((response) => response.json()),
    api("/v1/admin/usage").then((response) => response.json()),
    loadRate(),
  ]);
  state.clients = clients;
  state.rate = rate;
  state.usage = new Map(usage.clients.map((entry) => [entry.clientId, entry.totals]));
  renderClients();
  route();
}

const totalsOf = (client) => state.usage.get(client.id) ?? EMPTY_TOTALS;

function stat(label, value, sub) {
  return el("div", { class: "stat" },
    el("div", { class: "label", text: label }),
    el("div", { class: "value", text: value }),
    sub ? el("div", { class: "sub", text: sub }) : null);
}

function badge(active) {
  return el("span", { class: `badge ${active ? "on" : "off"}`, text: active ? "Ativo" : "Desativado" });
}

// ---------- lista de clientes ----------

function renderClients() {
  const all = [...state.usage.values()];
  const sum = (field) => all.reduce((total, totals) => total + Number(totals[field]), 0);
  const active = state.clients.filter((client) => client.active).length;
  $("#clients-stats").replaceChildren(
    stat("Clientes ativos", number.format(active), `${number.format(state.clients.length)} cadastrados`),
    stat("Chamadas", number.format(sum("calls")), "últimos 30 dias"),
    stat("Tokens", number.format(sum("promptTokens") + sum("outputTokens")), "entrada + saída, 30 dias"),
    stat("Custo estimado", cost(sum("estimatedCost")).main,
      state.rate ? `${money.format(sum("estimatedCost"))} · 30 dias` : "em dólar · 30 dias"));

  const rows = state.clients.map((client) => {
    const open = el("button", { type: "button", class: "row-link", "aria-label": `Ver uso de ${client.name}` },
      icon("M9 6l6 6-6 6"));
    const row = el("tr", { class: "clickable" },
      el("td", {}, el("div", { class: "client-cell" },
        el("span", { class: "client-initial", "aria-hidden": "true", text: client.name.charAt(0) }),
        client.name)),
      el("td", { class: "mono", text: `${client.apiKeyPrefix}…` }),
      el("td", { class: "num", text: number.format(client.rateLimitPerMinute) }),
      el("td", { class: "num", text: number.format(client.dailyQuota) }),
      el("td", {}, el("span", { class: "chip", text: client.defaultProvider ?? "padrão" })),
      el("td", {}, badge(client.active)),
      el("td", { class: "num" }, open));
    row.addEventListener("click", () => { location.hash = `cliente/${client.id}`; });
    return row;
  });
  $("#clients").replaceChildren(...(rows.length
    ? rows
    : [el("tr", {}, el("td", { class: "empty", colspan: "7", text: "Nenhum cliente cadastrado ainda." }))]));
}

// ---------- detalhe do cliente ----------

function renderClient(client) {
  const totals = totalsOf(client);
  const calls = Number(totals.calls);
  const usd = Number(totals.estimatedCost);

  $("#crumb-name").textContent = client.name;
  $("#client-title").textContent = client.name;
  $("#client-meta").textContent = `Chave ${client.apiKeyPrefix}… · cadastrado em ${dateFormat.format(new Date(client.createdAt))}`;

  const total = cost(usd);
  $("#usage-cost").textContent = total.main;
  $("#usage-cost-usd").textContent = total.detail;
  $("#usage-avg").textContent = calls ? cost(usd / calls).main : "—";
  $("#usage-success").textContent = calls ? percent.format(totals.successfulCalls / calls) : "—";

  $("#usage").replaceChildren(
    stat("Chamadas", number.format(calls)),
    stat("Com sucesso", number.format(totals.successfulCalls)),
    stat("Do cache", number.format(totals.cacheHits)),
    stat("Tokens de entrada", number.format(totals.promptTokens)),
    stat("Tokens de saída", number.format(totals.outputTokens)));

  const item = (label, value) => el("div", {}, el("dt", { text: label }), el("dd", {}, value));
  $("#client-config").replaceChildren(
    item("Situação", badge(client.active)),
    item("Requisições por minuto", number.format(client.rateLimitPerMinute)),
    item("Requisições por dia", number.format(client.dailyQuota)),
    item("Provedor padrão", client.defaultProvider ?? "Padrão do gateway"),
    item("Chave", el("span", { class: "mono", text: `${client.apiKeyPrefix}…` })));
}

// ---------- navegação (#cliente/{id}) ----------

function route() {
  const match = location.hash.match(/^#cliente\/(\d+)$/);
  const client = match && state.clients.find((candidate) => candidate.id === Number(match[1]));
  $("#clients-view").hidden = Boolean(client);
  $("#client-view").hidden = !client;
  if (client) renderClient(client);
  window.scrollTo(0, 0);
}

function goToList(event) {
  event.preventDefault();
  history.pushState(null, "", location.pathname);
  route();
}

// ---------- novo cliente ----------

function optionalNumber(input) {
  const value = input.value.trim();
  return value === "" ? undefined : Number(value);
}

function readClientForm() {
  const name = $("#client-name").value.trim();
  if (!name) throw new ApiError("Informe o nome do projeto.");
  for (const input of [$("#client-rate"), $("#client-quota")]) {
    if (!input.checkValidity()) {
      throw new ApiError(`${input.labels[0].textContent}: use um número inteiro entre ${input.min} e ${input.max}.`);
    }
  }
  return {
    name,
    rateLimitPerMinute: optionalNumber($("#client-rate")),
    dailyQuota: optionalNumber($("#client-quota")),
    defaultProvider: $("#client-provider").value || undefined,
  };
}

function showCreated({ client, apiKey }) {
  state.createdKey = apiKey;
  $("#created-name").textContent = client.name;
  $("#created-key").value = apiKey;
  $("#copy-key").textContent = "Copiar";
  $("#created").hidden = false;
  $("#created-key").select();
}

function toggleCreatePanel(open) {
  $("#create-panel").hidden = !open;
  if (open) {
    $("#created").hidden = true;
    $("#client-name").focus();
  }
}

// ---------- eventos ----------

$("#login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  run(event.submitter, async () => {
    const response = await request("/v1/admin/auth/login", {
      method: "POST",
      json: { email: $("#admin-email").value.trim(), password: $("#admin-password").value },
    }, false);
    setSession(await response.json());
    $("#admin-password").value = "";
    if (!state.user.mustChangePassword) await loadData();
  });
});

$("#password-form").addEventListener("submit", (event) => {
  event.preventDefault();
  run($("#password-submit"), async () => {
    const currentPassword = $("#current-password").value;
    const newPassword = $("#new-password").value;
    if (newPassword !== $("#confirm-password").value) throw new ApiError("A confirmação não confere com a nova senha.");
    if (!$("#new-password").checkValidity()) throw new ApiError("A nova senha deve ter entre 12 e 128 caracteres.");
    const response = await api("/v1/admin/auth/change-password", {
      method: "POST", json: { currentPassword, newPassword },
    });
    setSession(await response.json());
    event.target.reset();
    await loadData();
  });
});

for (const button of document.querySelectorAll(".js-logout")) {
  button.addEventListener("click", (event) => run(event.currentTarget, async () => {
    try { await api("/v1/admin/auth/logout", { method: "POST" }); } finally { clearSession(); }
  }));
}

$("#client-form").addEventListener("submit", (event) => {
  event.preventDefault();
  run($("#client-submit"), async () => {
    const response = await api("/v1/admin/clients", { method: "POST", json: readClientForm() });
    showCreated(await response.json());
    event.target.reset();
    await loadData();
  });
});

$("#copy-key").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(state.createdKey);
    $("#copy-key").textContent = "Copiado";
  } catch { $("#created-key").select(); }
});

$("#use-key").addEventListener("click", () => {
  store(CLIENT_KEY_STORAGE, state.createdKey);
  location.href = "index.html";
});

$("#new-client").addEventListener("click", () => toggleCreatePanel(true));
$("#create-close").addEventListener("click", () => toggleCreatePanel(false));
$("#clients-refresh").addEventListener("click", (event) => run(event.currentTarget, loadData));
$("#usage-refresh").addEventListener("click", (event) => run(event.currentTarget, loadData));
$("#client-back").addEventListener("click", goToList);
$("#nav-clients").addEventListener("click", goToList);
window.addEventListener("hashchange", route);

renderAccess();
loadCurrentSession();
