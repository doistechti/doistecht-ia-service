# Fase 6 — Extra

## Objetivo

Comprovar que a abstração de provedor funciona adicionando um segundo provedor (Ollama, rodando modelos localmente) e publicar um projeto cliente que demonstra o serviço sendo consumido na prática.

## Entregas

- Provedor Ollama implementando a interface `AiProvider`.
- Roteamento de provedor por requisição, por cliente ou por fallback.
- Projeto cliente de demonstração.
- README final do portfólio.

## Tarefas

### Provedor Ollama
- [ ] Dependência `spring-ai-starter-model-ollama`.
- [ ] Adicionar o Ollama ao `docker-compose.yml` como *profile* opcional (é pesado para rodar sempre).
- [ ] Implementar `OllamaProvider` (chat, streaming e embeddings).
- [ ] Nenhuma alteração nos controllers: só uma nova implementação de `AiProvider`.

### Roteamento de provedor
- [ ] `ProviderRouter` escolhendo o provedor por ordem de prioridade:
  1. Campo `provider` na requisição (se o cliente tiver permissão).
  2. Provedor padrão configurado no cliente.
  3. Provedor padrão global.
- [ ] Fallback entre provedores: Gemini indisponível → Ollama.
- [ ] Adicionar `default_provider` na tabela `client`.
- [ ] `GET /v1/providers`: lista provedores e modelos disponíveis com status de saúde.
- [ ] Health indicators do Actuator por provedor.

### Atenção aos embeddings
- [ ] Embeddings de modelos diferentes têm dimensões diferentes e **não são compatíveis**.
- [ ] Manter o provedor de embeddings fixo por coleção de documentos (sem fallback no RAG), ou usar tabelas vetoriais separadas por provedor.

### Projeto cliente de demonstração
- [ ] Repositório separado com um front-end simples (ou bot) consumindo o serviço.
- [ ] Demonstrar: chat com streaming, uma tarefa por template, output estruturado e RAG com upload de documento.
- [ ] Usar uma API key própria (cliente cadastrado no gateway).

### Documentação final
- [ ] README na raiz com: visão do projeto, diagrama de arquitetura, funcionalidades, como rodar, prints do Swagger e do Grafana e link para o projeto cliente.
- [ ] ADRs das principais decisões (Spring AI, Maven, Bucket4j, pgvector).
- [ ] Coleção de requisições (Postman/Insomnia ou arquivo `.http`).
- [ ] GIF ou vídeo curto do streaming e do RAG funcionando.

## Critérios de aceite

- [ ] Mesma requisição funciona com `provider: gemini` e `provider: ollama`.
- [ ] Com o Gemini simulado como indisponível, o serviço responde via Ollama.
- [ ] Nenhuma classe fora do pacote `provider` conhece Spring AI ou o provedor específico.
- [ ] Projeto cliente publicado e funcionando contra o serviço.
- [ ] README permite que outra pessoa rode o projeto do zero.
