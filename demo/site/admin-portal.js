// Portal administrativo. Todo texto vindo da API é inserido com textContent, nunca como HTML.
const SESSION_STORAGE = "ia-admin-session-token";
const CLIENT_KEY_STORAGE = "ia-demo-api-key";
const $ = (selector) => document.querySelector(selector);

const state = { token: readStored(SESSION_STORAGE), user: null, createdKey: "" };

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

function showError(message) {
  const alert = $("#alert");
  alert.textContent = message;
  alert.hidden = false;
}

function clearError() { $("#alert").hidden = true; }

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
  button.disabled = true;
  try { await action(); }
  catch (error) {
    if (error instanceof ApiError && error.status === 401 && state.user) clearSession();
    showError(error instanceof ApiError ? error.message : `Falha de comunicação: ${error.message}`);
  }
  finally { button.disabled = false; }
}

function setSession(session) {
  state.token = session.token;
  state.user = session.user;
  store(SESSION_STORAGE, state.token);
  renderAccess();
}

function clearSession() {
  state.token = "";
  state.user = null;
  store(SESSION_STORAGE, "");
  renderAccess();
}

function renderAccess() {
  const authenticated = Boolean(state.user);
  $("#login-form").hidden = authenticated;
  $("#admin-session").hidden = !authenticated;
  $("#first-access").hidden = !authenticated || !state.user.mustChangePassword;
  $("#admin-content").hidden = !authenticated || state.user.mustChangePassword;
  if (authenticated) $("#admin-identity").textContent = `${state.user.email} · ${state.user.role}`;
}

async function loadCurrentSession() {
  if (!state.token) return renderAccess();
  try {
    state.user = await (await api("/v1/admin/auth/me")).json();
    renderAccess();
    if (!state.user.mustChangePassword) await loadClients();
  } catch {
    clearSession();
  }
}

async function loadClients() {
  const clients = await (await api("/v1/admin/clients")).json();
  const rows = clients.map((client) => {
    const status = el("span", { class: "status" },
      el("span", { class: `dot ${client.active ? "READY" : "FAILED"}`, "aria-hidden": "true" }),
      client.active ? "Ativo" : "Desativado");
    return el("tr", {},
      el("td", { text: client.name }),
      el("td", { class: "mono", text: `${client.apiKeyPrefix}…` }),
      el("td", { text: client.rateLimitPerMinute }),
      el("td", { text: client.dailyQuota }),
      el("td", { text: client.defaultProvider ?? "padrão" }),
      el("td", {}, status));
  });
  $("#clients").replaceChildren(...(rows.length
    ? rows
    : [el("tr", {}, el("td", { class: "empty", colspan: "6", text: "Nenhum cliente cadastrado." }))]));
}

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

$("#login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  run(event.submitter, async () => {
    const response = await request("/v1/admin/auth/login", {
      method: "POST",
      json: { email: $("#admin-email").value.trim(), password: $("#admin-password").value },
    }, false);
    setSession(await response.json());
    $("#admin-password").value = "";
    if (!state.user.mustChangePassword) await loadClients();
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
    await loadClients();
  });
});

$("#logout").addEventListener("click", (event) => run(event.currentTarget, async () => {
  try { await api("/v1/admin/auth/logout", { method: "POST" }); } finally { clearSession(); }
}));

$("#client-form").addEventListener("submit", (event) => {
  event.preventDefault();
  run($("#client-submit"), async () => {
    const response = await api("/v1/admin/clients", { method: "POST", json: readClientForm() });
    showCreated(await response.json());
    event.target.reset();
    await loadClients();
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

$("#clients-refresh").addEventListener("click", (event) => run(event.currentTarget, loadClients));
renderAccess();
loadCurrentSession();
