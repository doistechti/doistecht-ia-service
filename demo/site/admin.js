// Cadastro de clientes do doistecht-ia-service (rotas /v1/admin/clients).
// Como em app.js, todo texto vindo da API é inserido com textContent, nunca como HTML.

const ADMIN_KEY_STORAGE = "ia-demo-admin-key";

// Mesma chave usada por app.js: "Usar na demo" já deixa a demo conectada ao cliente novo
const CLIENT_KEY_STORAGE = "ia-demo-api-key";

const $ = (selector) => document.querySelector(selector);

const state = {
  adminKey: readStored(ADMIN_KEY_STORAGE),
  createdKey: "",
};

// ---------- utilidades ----------

function readStored(name) {
  try {
    return sessionStorage.getItem(name) ?? "";
  } catch {
    return "";
  }
}

function store(name, value) {
  try {
    sessionStorage.setItem(name, value);
  } catch {
    // Sem sessionStorage: o valor vale só até recarregar
  }
}

function el(tag, attributes = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (name === "class") node.className = value;
    else if (name === "text") node.textContent = value;
    else node.setAttribute(name, value);
  }
  for (const child of children.flat()) {
    if (child != null) node.append(child);
  }
  return node;
}

function showError(message) {
  const alert = $("#alert");
  alert.textContent = message;
  alert.hidden = false;
}

function clearError() {
  $("#alert").hidden = true;
}

class ApiError extends Error {}

// Converte respostas de erro (ProblemDetail) em mensagens legíveis
async function toError(response) {
  let detail = `Erro ${response.status}`;
  try {
    const problem = await response.json();
    detail = problem.detail ?? detail;
    if (problem.errors && !Array.isArray(problem.errors)) {
      detail += " " + Object.entries(problem.errors).map(([field, msg]) => `${field}: ${msg}`).join("; ");
    }
  } catch {
    // corpo não é JSON
  }
  if (response.status === 401) detail += " Use a chave de administrador (IA_SERVICE_ADMIN_KEY), não a de um cliente.";
  return new ApiError(detail);
}

async function api(path, options = {}) {
  if (!state.adminKey) throw new ApiError("Informe a chave de administrador no topo da página.");
  const headers = { "X-API-Key": state.adminKey, ...(options.headers ?? {}) };
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    options = { ...options, body: JSON.stringify(options.json) };
  }
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) throw await toError(response);
  return response;
}

async function run(button, action) {
  clearError();
  button.disabled = true;
  try {
    await action();
  } catch (error) {
    showError(error instanceof ApiError ? error.message : `Falha de comunicação: ${error.message}`);
  } finally {
    button.disabled = false;
  }
}

// ---------- listagem ----------

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

// ---------- cadastro ----------

function optionalNumber(input) {
  const value = input.value.trim();
  return value === "" ? undefined : Number(value);
}

function readForm() {
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

function setupForm() {
  $("#client-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run($("#client-submit"), async () => {
      const response = await api("/v1/admin/clients", { method: "POST", json: readForm() });
      showCreated(await response.json());
      event.target.reset();
      await loadClients();
    });
  });

  $("#copy-key").addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(state.createdKey);
      $("#copy-key").textContent = "Copiado";
    } catch {
      // Sem permissão de área de transferência: deixa a chave selecionada para Ctrl+C
      $("#created-key").select();
    }
  });

  $("#use-key").addEventListener("click", () => {
    store(CLIENT_KEY_STORAGE, state.createdKey);
    location.href = "index.html";
  });
}

// ---------- inicialização ----------

function setupKeyForm() {
  $("#admin-key").value = state.adminKey;
  $("#admin-key-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.adminKey = $("#admin-key").value.trim();
    store(ADMIN_KEY_STORAGE, state.adminKey);
    run(event.submitter, loadClients);
  });
}

setupKeyForm();
setupForm();
$("#clients-refresh").addEventListener("click", (event) => run(event.currentTarget, loadClients));
if (state.adminKey) loadClients().catch((error) => showError(error.message));
