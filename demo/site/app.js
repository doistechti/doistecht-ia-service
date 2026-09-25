// Projeto cliente de demonstração do doistecht-ia-service.
// Todo texto vindo da API (inclusive respostas do modelo) é inserido com textContent,
// nunca como HTML, para que conteúdo gerado ou de documentos não execute código na página.

const KEY_STORAGE = "ia-demo-api-key";

const CLIENT_KEY_PREFIX = "dtia_";

// A chave de administrador (IA_SERVICE_ADMIN_KEY) não funciona nas rotas de cliente
const CLIENT_KEY_HINT = "Use a API key de um cliente: ela começa com dtia_ e é gerada ao cadastrar um cliente "
  + "(POST /v1/admin/clients, com a chave de administrador). A chave de administrador não funciona aqui.";

const TEMPLATES = {
  "resumir-texto": {
    description: "Resume um texto em até N linhas.",
    fields: [
      { name: "texto", label: "Texto", multiline: true, value: "O Spring AI é um projeto do ecossistema Spring que facilita a integração de modelos de IA em aplicações Java. Ele oferece abstrações para chat, embeddings e busca vetorial, com suporte a diversos provedores, como Gemini, OpenAI e Ollama." },
      { name: "linhas", label: "Máximo de linhas", value: "2" },
    ],
  },
  "classificar-ticket": {
    description: "Classifica um ticket de atendimento e responde em JSON (categoria, prioridade e resumo).",
    fields: [
      { name: "ticket", label: "Ticket", multiline: true, value: "Fui cobrado duas vezes pela assinatura deste mês e preciso do estorno com urgência." },
    ],
  },
  "gerar-descricao-produto": {
    description: "Escreve uma descrição de venda para um produto.",
    fields: [
      { name: "produto", label: "Produto", value: "Garrafa térmica 1 L" },
      { name: "caracteristicas", label: "Características", value: "aço inox, mantém a temperatura por 24 h, tampa antivazamento" },
      { name: "tom", label: "Tom", value: "descontraído" },
    ],
  },
};

const DEFAULT_SCHEMA = {
  type: "object",
  properties: {
    nome: { type: "string" },
    idade: { type: "integer" },
    profissao: { type: "string" },
    cidade: { type: "string" },
    idiomas: { type: "array", items: { type: "string" } },
  },
  required: ["nome", "idade", "cidade"],
};

const $ = (selector) => document.querySelector(selector);

const state = {
  apiKey: readStoredKey(),
  history: [],
  pollTimer: null,
};

// ---------- utilidades ----------

function readStoredKey() {
  try {
    return sessionStorage.getItem(KEY_STORAGE) ?? "";
  } catch {
    return "";
  }
}

function storeKey(key) {
  try {
    sessionStorage.setItem(KEY_STORAGE, key);
  } catch {
    // Sem sessionStorage (ex.: navegação privada restrita): a chave vale só até recarregar
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

function selectedProvider() {
  return $("#provider").value || undefined;
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
    if (problem.missingVariables) detail += ` Variáveis: ${problem.missingVariables.join(", ")}.`;
    if (problem.errors && !Array.isArray(problem.errors)) {
      detail += " " + Object.entries(problem.errors).map(([field, msg]) => `${field}: ${msg}`).join("; ");
    }
  } catch {
    // corpo não é JSON
  }
  const retryAfter = response.headers.get("Retry-After");
  if (retryAfter) detail += ` Tente novamente em ${retryAfter} s.`;
  if (response.status === 401) detail += ` ${CLIENT_KEY_HINT}`;
  return new ApiError(detail);
}

function updateLimits(response) {
  const minute = response.headers.get("X-RateLimit-Remaining");
  const quota = response.headers.get("X-Quota-Remaining");
  if (minute != null && quota != null) {
    $("#limits").textContent = `Restam ${minute} requisições neste minuto · ${quota} hoje`;
  }
}

async function api(path, options = {}) {
  if (!state.apiKey) throw new ApiError("Informe a API key do cliente no topo da página.");
  const headers = { "X-API-Key": state.apiKey, ...(options.headers ?? {}) };
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    options = { ...options, body: JSON.stringify(options.json) };
  }
  const response = await fetch(path, { ...options, headers });
  updateLimits(response);
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

function badges(result) {
  const list = [];
  if (result.provider) list.push(el("span", { class: "badge", text: `provedor: ${result.provider}` }));
  if (result.model) list.push(el("span", { class: "badge", text: `modelo: ${result.model}` }));
  if (result.fallback) list.push(el("span", { class: "badge warn", text: "resposta do provedor/modelo reserva" }));
  return el("div", { class: "badges" }, list);
}

// ---------- abas ----------

function setupTabs() {
  const tabs = [...document.querySelectorAll('[role="tab"]')];
  for (const tab of tabs) {
    tab.addEventListener("click", () => {
      for (const other of tabs) {
        const selected = other === tab;
        other.setAttribute("aria-selected", String(selected));
        document.getElementById(other.getAttribute("aria-controls")).hidden = !selected;
      }
      if (tab.id === "t-rag") loadDocuments();
      if (tab.id === "t-usage") loadUsage();
    });
  }
}

// ---------- provedores ----------

async function loadProviders() {
  const statusList = $("#provider-status");
  statusList.replaceChildren();
  const select = $("#provider");
  select.replaceChildren(el("option", { value: "", text: "Automático (padrão do gateway)" }));
  if (!state.apiKey) return;

  const providers = await (await api("/v1/providers")).json();
  for (const provider of providers) {
    const roles = [provider.defaultProvider && "padrão", provider.fallbackProvider && "reserva"].filter(Boolean);
    const label = `${provider.name} (${provider.models.chatModel})${roles.length ? " · " + roles.join(", ") : ""}`;
    select.append(el("option", { value: provider.name, text: label }));
    statusList.append(el("li", { title: provider.detail ?? "" },
      el("span", { class: `dot ${provider.status}`, "aria-hidden": "true" }),
      `${provider.name}: ${provider.status}`));
  }
}

// ---------- chat com streaming ----------

function appendMessage(role, text) {
  const messages = $("#messages");
  messages.querySelector(".empty")?.remove();
  const bubble = el("div", { class: `message ${role}` });
  const body = el("span", { text });
  bubble.append(body);
  messages.append(bubble);
  messages.scrollTop = messages.scrollHeight;
  return { bubble, body };
}

// Lê a resposta SSE do POST (EventSource não permite POST nem headers personalizados)
async function* readEvents(response) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const raw = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let event = "message";
      const data = [];
      for (const line of raw.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5));
      }
      if (data.length) yield { event, data: JSON.parse(data.join("\n")) };
    }
  }
}

async function sendChat(message) {
  appendMessage("user", message);
  const { bubble, body } = appendMessage("assistant", "…");
  let answer = "";
  try {
    const response = await api("/v1/chat/stream", {
      method: "POST",
      headers: { Accept: "text/event-stream" },
      json: { message, history: state.history, provider: selectedProvider() },
    });
    for await (const { event, data } of readEvents(response)) {
      if (event === "message") {
        answer += data.content;
        body.textContent = answer;
        $("#messages").scrollTop = $("#messages").scrollHeight;
      } else if (event === "done") {
        bubble.append(el("span", { class: "meta", text: data.model ? `modelo: ${data.model}` : "" }));
      } else if (event === "error") {
        throw new ApiError(data.detail ?? "Falha durante a geração da resposta.");
      }
    }
  } catch (error) {
    // Sem isto a bolha ficaria mostrando "…" para sempre
    body.textContent = answer || "(sem resposta — veja o aviso acima)";
    bubble.classList.add("failed");
    throw error;
  }
  state.history.push({ role: "user", content: message }, { role: "assistant", content: answer });
}

function setupChat() {
  $("#chat-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = $("#chat-input");
    const message = input.value.trim();
    if (!message) return;
    input.value = "";
    run($("#chat-send"), () => sendChat(message));
  });
  $("#chat-input").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      $("#chat-form").requestSubmit();
    }
  });
  $("#chat-clear").addEventListener("click", () => {
    state.history = [];
    $("#messages").replaceChildren(el("p", { class: "empty", text: "Nova conversa iniciada." }));
  });
}

// ---------- tarefas ----------

function renderTaskFields() {
  const template = TEMPLATES[$("#task-template").value];
  $("#task-description").textContent = template.description;
  $("#task-fields").replaceChildren(...template.fields.map((field) => {
    const id = `field-${field.name}`;
    const input = field.multiline
      ? el("textarea", { id, rows: "4", name: field.name })
      : el("input", { id, type: "text", name: field.name });
    input.value = field.value;
    return el("div", { class: "stack" }, el("label", { for: id, text: field.label }), input);
  }));
}

function setupTasks() {
  const select = $("#task-template");
  for (const name of Object.keys(TEMPLATES)) select.append(el("option", { value: name, text: name }));
  select.addEventListener("change", renderTaskFields);
  renderTaskFields();

  $("#task-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const button = event.submitter;
    run(button, async () => {
      const variables = Object.fromEntries(
        [...$("#task-fields").querySelectorAll("input, textarea")].map((input) => [input.name, input.value]));
      const response = await api(`/v1/tasks/${select.value}`, {
        method: "POST",
        json: { variables, provider: selectedProvider() },
      });
      const result = await response.json();
      const output = result.data !== undefined
        ? el("pre", { text: JSON.stringify(result.data, null, 2) })
        : el("div", { class: "answer", text: result.content });
      const box = $("#task-result");
      box.replaceChildren(el("div", { class: "hint", text: `${result.template} · versão ${result.version}` }), output, badges(result));
      box.hidden = false;
    });
  });
}

// ---------- output estruturado ----------

function setupStructured() {
  $("#structured-schema").value = JSON.stringify(DEFAULT_SCHEMA, null, 2);
  $("#structured-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run(event.submitter, async () => {
      let schema;
      try {
        schema = JSON.parse($("#structured-schema").value);
      } catch {
        throw new ApiError("O JSON Schema não é um JSON válido.");
      }
      const response = await api("/v1/structured", {
        method: "POST",
        json: { input: $("#structured-input").value, schema, provider: selectedProvider() },
      });
      const result = await response.json();
      const box = $("#structured-result");
      box.replaceChildren(el("pre", { text: JSON.stringify(result.data, null, 2) }), badges(result));
      box.hidden = false;
    });
  });
}

// ---------- documentos e RAG ----------

const STATUS_LABEL = { PROCESSING: "Processando", READY: "Pronto", FAILED: "Falhou" };

async function loadDocuments() {
  if (!state.apiKey) return;
  try {
    const documents = await (await api("/v1/documents")).json();
    renderDocuments(documents);
    clearTimeout(state.pollTimer);
    // Enquanto houver documento em processamento, consulta de novo em 2 s
    if (documents.some((doc) => doc.status === "PROCESSING")) {
      state.pollTimer = setTimeout(loadDocuments, 2000);
    }
  } catch (error) {
    showError(error.message);
  }
}

function renderDocuments(documents) {
  const rows = documents.map((doc) => {
    const remove = el("button", { class: "link", type: "button", text: "Apagar" });
    remove.addEventListener("click", () => run(remove, async () => {
      await api(`/v1/documents/${doc.id}`, { method: "DELETE" });
      await loadDocuments();
    }));
    const status = el("span", { class: "status" },
      el("span", { class: `dot ${doc.status}`, "aria-hidden": "true" }), STATUS_LABEL[doc.status] ?? doc.status);
    return el("tr", {},
      el("td", { text: doc.fileName }),
      el("td", {}, status, doc.errorMessage ? el("span", { class: "status-detail", text: doc.errorMessage }) : null),
      el("td", { text: doc.chunkCount ?? "–" }),
      el("td", {}, remove));
  });
  $("#documents").replaceChildren(...(rows.length
    ? rows
    : [el("tr", {}, el("td", { class: "empty", colspan: "4", text: "Nenhum documento enviado." }))]));
}

function setupRag() {
  $("#upload-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const file = $("#upload-file").files[0];
    if (!file) return;
    run(event.submitter, async () => {
      const form = new FormData();
      form.append("file", file);
      await api("/v1/documents", { method: "POST", body: form });
      $("#upload-file").value = "";
      await loadDocuments();
    });
  });

  $("#rag-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run(event.submitter, async () => {
      const response = await api("/v1/rag/ask", {
        method: "POST",
        json: { question: $("#rag-question").value, provider: selectedProvider() },
      });
      const result = await response.json();
      const children = [el("div", { class: "answer", text: result.answer })];
      if (result.found) {
        children.push(badges(result), el("div", { class: "hint", text: "Fontes usadas:" }), el("ol", { class: "sources" },
          result.sources.map((source) => el("li", {},
            el("div", { class: "source-title", text: `${source.fileName} · trecho ${source.chunkIndex} · similaridade ${source.score}` }),
            el("div", { class: "excerpt", text: source.excerpt })))));
      } else {
        children.push(el("div", { class: "hint", text: "Nenhum trecho relevante: o modelo de chat nem foi chamado." }));
      }
      const box = $("#rag-result");
      box.replaceChildren(...children);
      box.hidden = false;
    });
  });
}

// ---------- uso ----------

async function loadUsage() {
  if (!state.apiKey) return;
  try {
    const { totals } = await (await api("/v1/usage")).json();
    const number = new Intl.NumberFormat("pt-BR");
    const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "USD", maximumFractionDigits: 4 });
    const tiles = [
      ["Chamadas", number.format(totals.calls)],
      ["Com sucesso", number.format(totals.successfulCalls)],
      ["Do cache", number.format(totals.cacheHits)],
      ["Tokens de entrada", number.format(totals.promptTokens)],
      ["Tokens de saída", number.format(totals.outputTokens)],
      ["Custo estimado", money.format(totals.estimatedCost)],
    ];
    $("#usage").replaceChildren(...tiles.map(([label, value]) =>
      el("div", { class: "tile" }, el("div", { class: "label", text: label }), el("div", { class: "value", text: value }))));
  } catch (error) {
    showError(error.message);
  }
}

// ---------- inicialização ----------

function setupKeyForm() {
  $("#api-key").value = state.apiKey;
  $("#key-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.apiKey = $("#api-key").value.trim();
    storeKey(state.apiKey);
    if (state.apiKey && !state.apiKey.startsWith(CLIENT_KEY_PREFIX)) {
      showError(`Essa não parece ser a API key de um cliente. ${CLIENT_KEY_HINT}`);
      return;
    }
    run(event.submitter, loadProviders);
  });
}

setupTabs();
setupKeyForm();
setupChat();
setupTasks();
setupStructured();
setupRag();
$("#usage-refresh").addEventListener("click", loadUsage);
if (state.apiKey) loadProviders().catch((error) => showError(error.message));
