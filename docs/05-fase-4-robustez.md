# Fase 4 — Robustez

## Objetivo

Deixar o serviço confiável e com qualidade verificável: tolerância a falhas do provedor, testes automatizados cobrindo os fluxos principais e pipeline de CI.

## Entregas

- Retry, circuit breaker e timeout.
- Fallback para modelo secundário.
- Testes unitários e de integração separados (Surefire/Failsafe), com WireMock simulando o Gemini.
- Pipeline de CI no GitHub Actions.
- Relatório e verificação de cobertura de testes.

## Tarefas

### Resiliência
- [x] Dependência `resilience4j-spring-boot4`.
- [x] **Retry** com backoff exponencial apenas para erros transitórios (`408`, `429`, `5xx`, timeout/IO). Não retentar `400`/`401`/`403`/`404`.
- [x] **Circuit breaker** por provedor/modelo (`gemini:<modelo>`), contando só erros transitórios.
- [x] **Timeout** por chamada, no cliente HTTP do Gemini (`ia-service.gemini.timeout`).
- [x] **Fallback**: se o modelo principal falhar por erro transitório ou estiver com o circuito aberto, usar o modelo reserva (`ia-service.gemini.fallback-model`).
- [x] Informar na resposta quando o fallback foi usado (campo `fallback: true` em chat, structured e tasks).
- [x] Mapear erros do provedor: `503` + `Retry-After` (indisponível / circuito aberto) e `502` (erro permanente), ambos em `ProblemDetail`.
- [x] Desabilitar as novas tentativas escondidas: retry interno do Spring AI (`spring.ai.retry.max-attempts: 0`) e retry do SDK do Google (`HttpRetryOptions.attempts(1)`).

### Testes unitários
- [x] JUnit 5 + Mockito + AssertJ.
- [x] Renderização de templates e validação de variáveis.
- [x] Geração e verificação de hash de API keys.
- [x] Cálculo de custo estimado e geração da chave de cache.
- [x] `ModelFallbackExecutor`: retry, fallback, erro permanente, circuito aberto e `Retry-After`.
- [x] `GeminiErrors`: classificação de erros transitórios e permanentes.

### Testes de integração
- [x] **WireMock** simulando a API do Gemini: sucesso, `429`, `503`, `400`, timeout e resposta em streaming.
- [x] **Testcontainers** para PostgreSQL e Redis (`@ServiceConnection`), como containers únicos compartilhados entre as classes.
- [x] Cenários ponta a ponta:
  - [x] Chat com sucesso grava `usage_record`.
  - [x] Falha transitória é retentada e depois responde com sucesso.
  - [x] Modelo principal fora do ar (ou lento) aciona o fallback.
  - [x] Circuitos abertos respondem `503` na hora, sem chamar o Gemini.
  - [x] Rate limit retorna `429`.
  - [x] Cache evita a segunda chamada ao Gemini (verificado no WireMock); respostas do fallback não são cacheadas.
  - [x] Chave inválida retorna `401`.

### Qualidade
- [x] JaCoCo gerando relatório de cobertura (`mvn verify`), com unitários e integração no mesmo arquivo.
- [x] Cobertura mínima exigida pelo build: 80% das linhas (meta original: 70%). Cobertura atual: ~93%.
- [x] Separar testes unitários (`*Test`, Surefire) e de integração (`*IT`, Failsafe).

### CI (GitHub Actions)
- [x] Workflow `.github/workflows/ci.yml` disparado em push, pull request e manualmente.
- [x] Etapas: checkout → Java 21 com cache do Maven → `./mvnw -B verify`.
- [x] Publicar relatórios de testes e cobertura como artefatos.
- [x] Build da imagem Docker na branch `main`.
- [x] Badge de status do CI no README.

## Decisões tomadas

- **Três camadas de retry foram reduzidas a uma.** O SDK do Google repetia sozinho até 5 vezes (408/429/5xx, com espera de até 60s) e o Spring AI tem retry próprio (até 10 tentativas para alguns erros). Empilhadas sob o Resilience4j, uma falha poderia virar dezenas de chamadas. Agora só o Resilience4j retenta, com regras explícitas.
- **O SDK do Google não tinha timeout.** Por padrão ele remove os timeouts do OkHttp; uma chamada travada ficaria presa para sempre. O timeout agora é configurado no cliente HTTP (`GeminiClientConfig`), no lugar do `TimeLimiter` do Resilience4j previsto originalmente: cancela a chamada de verdade, sem precisar de uma thread extra.
- **Resiliência programática (`ModelFallbackExecutor`), não por anotações.** Cada modelo tem seu próprio circuit breaker e retry, criados dinamicamente; com anotações (`@Retry`, `@CircuitBreaker`) isso exigiria nomes fixos e métodos de fallback separados. A classe é genérica e será reaproveitada pelo provedor Ollama na fase 6.
- **Erros permanentes não abrem o circuito nem acionam o fallback:** um prompt inválido falharia do mesmo jeito no modelo reserva.
- **Respostas do modelo reserva não vão para o cache:** quando o principal voltar, os clientes não devem continuar recebendo a resposta do modelo mais fraco.
- **Streaming sem retry e sem fallback:** depois que o primeiro trecho chega ao cliente, não há como recomeçar a resposta de forma transparente.
- **Cliente Redis próprio para o Bucket4j.** Os testes revelaram que, ao parar e reiniciar o `LettuceConnectionFactory` do Spring, o Bucket4j ficava preso a uma conexão fechada, e o rate limit passava a liberar tudo (fail open) para sempre. Com um cliente próprio, encerrado só no fim da aplicação, a reconexão automática do Lettuce resolve quedas do Redis.
- **Pior caso de latência:** com os padrões (3 tentativas, 30s de timeout), uma chamada em que tanto o modelo principal quanto o reserva travam pode levar alguns minutos antes de responder `503`. Erros rápidos (`429`/`503`) respondem em poucos segundos, e o circuit breaker passa a responder na hora depois de falhas repetidas.

## Configuração

```yaml
spring.ai.retry.max-attempts: 0      # sem retry do Spring AI

ia-service:
  gemini:
    timeout: 30s
    fallback-model: gemini-2.5-flash-lite

resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

## Critérios de aceite

- [x] Falhas simuladas do Gemini não derrubam o serviço e geram respostas claras.
- [x] Fallback comprovado por teste automatizado.
- [x] `mvn verify` roda todos os testes localmente com apenas Docker instalado.
- [x] CI verde na `main` e badge visível no README.
- [x] Nenhum teste depende da API real do Gemini.

## Status

Implementada. 111 testes automatizados (78 unitários e 33 de integração) passando com `mvn verify`, e cobertura de ~93% das linhas.
Validado com `docker compose up`: o serviço sobe com a nova configuração, e um erro permanente do Gemini responde `502` na hora, sem retry nem fallback.
CI confirmado verde no GitHub (build, testes e imagem Docker), inclusive após a atualização das actions para as versões mais recentes.
**Pendente:** validar com chave real do Gemini.

## Fora desta fase

Métricas em dashboard e RAG (fase 5).
