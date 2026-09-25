# Fase 6 — Extra

## Objetivo

Comprovar que a abstração de provedor funciona adicionando um segundo provedor (Ollama, rodando modelos localmente), publicar um projeto cliente que demonstra o serviço sendo consumido na prática e fechar o portfólio.

## Entregas

- Provedor Ollama implementando o contrato de provedor.
- Roteamento de provedor por requisição, por cliente e por fallback.
- Projeto cliente de demonstração.
- README final do portfólio, guia da API, ADRs e coleção de requisições.
- Validação de ponta a ponta com a API real do Gemini.

## Tarefas

### Provedor Ollama
- [x] Dependência `spring-ai-starter-model-ollama`.
- [x] Ollama no `docker-compose.yml` como *profile* opcional (`ollama`), com um container que baixa os modelos na primeira execução.
- [x] `OllamaProvider` com chat, streaming, output estruturado (campo `format` com JSON Schema) e embeddings (`nomic-embed-text`, com os prefixos `search_document:` / `search_query:`).
- [x] Cliente HTTP próprio: timeout de conexão curto (2 s), de leitura longo (120 s) e HTTP/1.1.
- [x] Nenhuma alteração nos controllers: só um novo bean de provedor.

### Roteamento de provedor
- [x] Interfaces separadas: `ModelProvider` (provedor concreto) e `AiProvider` (porta de entrada, implementada pelo gateway).
- [x] `ProviderRouter`: provedor da requisição → padrão do cliente → padrão global.
- [x] Fallback entre provedores (Gemini indisponível → Ollama), só quando a requisição não pediu um provedor específico.
- [x] No streaming, fallback de modelo e de provedor antes do primeiro trecho.
- [x] Coluna `default_provider` na tabela `client` e campo `defaultProvider` na API de admin.
- [x] `GET /v1/providers`: provedores, modelos e situação de cada um.
- [x] Health indicator `aiProviders` no Actuator.

### Embeddings
- [x] Provedor de embeddings do RAG fixo (`RAG_EMBEDDING_PROVIDER`), sem fallback.
- [x] Busca vetorial filtrando pelo modelo que indexou cada documento.

### Projeto cliente de demonstração
- [x] Pasta `demo/` neste repositório (decisão desta fase), servida pelo nginx no `docker compose`.
- [x] Chat com streaming, tarefas por template, output estruturado, documentos com RAG e consumo do cliente.
- [x] API key própria (cliente cadastrado no gateway), guardada só na aba do navegador.

### Documentação final
- [x] README com visão do projeto, diagrama de arquitetura, como rodar e prints da demo e do Grafana.
- [x] ADRs das principais decisões ([`docs/adr/`](adr)).
- [x] Guia da API ([`08-guia-da-api.md`](08-guia-da-api.md)) e coleção de requisições ([`requests.http`](../requests.http)).
- [ ] GIF ou vídeo curto do streaming e do RAG funcionando (substituído pelos prints; pode ser gravado depois).

## Validação com a API real do Gemini

Os testes automatizados simulam as APIs (WireMock). Nesta fase, o stack completo também foi testado com uma chave real do Google AI Studio (plano gratuito). O teste real encontrou problemas que a simulação não pegaria:

| O que o teste real mostrou | O que foi feito |
|---|---|
| O `gemini-2.5-flash`, padrão desde a fase 1, **não está mais disponível para contas novas** (`404`: "no longer available to new users") | Modelos consultados na API da chave; padrões trocados para `gemini-3.8-flash` (principal) e `gemini-3.5-flash-lite` (reserva), com os preços da página oficial |
| O `gemini-3.8-flash` respondeu `503` ("high demand") com frequência | As chamadas síncronas já trocavam para o reserva (fase 4); o **streaming falhava** sempre, então ganhou fallback antes do primeiro trecho |
| Com `RAG_MIN_SCORE=0.5`, uma pergunta sem relação com o documento (similaridade ~0,55) passava pelo filtro e gastava uma chamada de chat | Padrão ajustado para **0,6**: perguntas relevantes ficaram acima de 0,64 |
| Todas as abas da demo apareciam ao mesmo tempo (`display: grid` vencia o atributo `hidden`) | Regra `[hidden] { display: none !important; }` |
| O `curl` do Git Bash enviava acentos fora de UTF-8 | Só no ambiente de teste: corpos enviados de arquivos UTF-8 |

Funcionou com a API real: chat, streaming (com troca para o reserva), output estruturado, tarefa com schema, RAG com embeddings reais e fontes, registro de tokens e custo, cache, métricas no Grafana e a demo pelo nginx.

## Decisões tomadas

- **Demo na pasta `demo/`, em HTML e JavaScript puro com nginx:** sobe com o resto no `docker compose` e não adiciona Node ao build. O nginx repassa `/v1` ao gateway (mesma origem, sem CORS) com buffering desligado para o streaming, e não expõe o Actuator nem o Swagger.
- **Pedido explícito de provedor é respeitado:** sem troca silenciosa. Um cliente que pediu o Ollama (ex.: para não enviar dados à nuvem) nunca recebe resposta do Gemini.
- **Sem permissão por cliente para escolher provedor:** os dois provedores são gratuitos e internos; fica como evolução.
- **Health check `UP` enquanto algum provedor responde:** um provedor opcional desligado não deve derrubar o serviço.
- **Evento `done` do streaming com o modelo que respondeu**, para o cliente saber quando veio do reserva.
- **Roteador busca os provedores pelo nome a cada chamada**, em vez de montar um mapa na subida: com dois provedores o custo é irrelevante, e os testes com mocks ficam corretos.

Detalhes em [ADR 0007](adr/0007-roteamento-entre-provedores.md) e [ADR 0004](adr/0004-uma-camada-de-retry.md).

## Critérios de aceite

- [x] Mesma requisição funciona com `provider: gemini` e `provider: ollama` (testes com WireMock simulando as duas APIs).
- [x] Com o Gemini indisponível, o serviço responde via Ollama (teste automatizado).
- [x] Nenhuma classe fora do pacote `provider` conhece o Spring AI ou o provedor específico.
- [x] Projeto cliente publicado e funcionando contra o serviço (validado com o Gemini real).
- [x] README permite que outra pessoa rode o projeto do zero.

## Status

Implementada. 173 testes automatizados passando com `mvn verify` (116 unitários e 57 de integração), cobertura de ~93%.
Validado com a API real do Gemini (plano gratuito) e com `docker compose up`, incluindo a demo e o Grafana com tráfego real.
**Não validado com Ollama real:** o provedor Ollama foi testado com a API simulada pelo WireMock; rodar os modelos locais exige baixar ~2,5 GB.

## Limitações conhecidas

- Quando o modelo principal está sobrecarregado, o Gemini às vezes demora ~25 s para responder `503`, e o streaming espera esse tempo antes de trocar para o reserva.
- A calibração da similaridade mínima do RAG usou poucas perguntas; deve ser revista com documentos reais.
- A API key do cliente fica no navegador na demo. Em produção, ela deve ficar no servidor do projeto cliente.
