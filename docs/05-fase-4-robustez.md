# Fase 4 — Robustez

## Objetivo

Deixar o serviço confiável e com qualidade verificável: tolerância a falhas do provedor, testes automatizados cobrindo os fluxos principais e pipeline de CI.

## Entregas

- Retry, circuit breaker e timeout com Resilience4j.
- Fallback para modelo secundário.
- Testes unitários e de integração (WireMock + Testcontainers).
- Pipeline de CI no GitHub Actions.
- Relatório de cobertura de testes.

## Tarefas

### Resiliência
- [ ] Dependência do starter do Resilience4j compatível com Spring Boot 4 + suporte a AOP.
- [ ] **Retry** com backoff exponencial apenas para erros transitórios (`429`, `500`, `503`, timeout). Não retentar `400`/`401`.
- [ ] **Circuit breaker** por provedor/modelo: abre após taxa de falha configurada e evita martelar o Gemini.
- [ ] **Time limiter**: timeout máximo por chamada.
- [ ] **Fallback**: se o modelo principal falhar ou o circuito estiver aberto, usar o modelo secundário configurado.
- [ ] Informar na resposta quando o fallback foi usado (campo `fallback: true`).
- [ ] Mapear erros do provedor para respostas claras ao cliente (`503` com `ProblemDetail`).
- [ ] Desabilitar o retry interno do Spring AI (se houver) para não duplicar tentativas com o Resilience4j.

### Testes unitários
- [ ] JUnit 5 + Mockito + AssertJ.
- [ ] Renderização de templates e validação de variáveis.
- [ ] Geração e verificação de hash de API keys.
- [ ] Cálculo de custo estimado e geração da chave de cache.

### Testes de integração
- [ ] **WireMock** simulando a API do Gemini: sucesso, `429`, `503`, timeout e resposta em streaming.
- [ ] **Testcontainers** para PostgreSQL e Redis (`@ServiceConnection`).
- [ ] Cenários ponta a ponta:
  - [ ] Chat com sucesso grava `usage_record`.
  - [ ] Falha transitória é retentada e depois responde com sucesso.
  - [ ] Circuito aberto aciona o fallback.
  - [ ] Rate limit retorna `429`.
  - [ ] Cache evita a segunda chamada ao Gemini (verificado no WireMock).
  - [ ] Chave inválida retorna `401`.

### Qualidade
- [ ] JaCoCo gerando relatório de cobertura (`mvn verify`).
- [ ] Meta inicial: 70% de cobertura nas camadas de serviço.
- [ ] Separar testes unitários (Surefire) e de integração (Failsafe).

### CI (GitHub Actions)
- [ ] Workflow `.github/workflows/ci.yml` disparado em push e pull request.
- [ ] Etapas: checkout → setup Java 21 com cache do Maven → `mvn verify`.
- [ ] Publicar relatório de testes e cobertura como artefato.
- [ ] Build da imagem Docker na branch `main`.
- [ ] Badge de status do CI no README.

## Configuração de exemplo

```yaml
resilience4j:
  retry:
    instances:
      gemini:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
  circuitbreaker:
    instances:
      gemini:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  timelimiter:
    instances:
      gemini:
        timeout-duration: 30s
```

## Critérios de aceite

- [ ] Falhas simuladas do Gemini não derrubam o serviço e geram respostas claras.
- [ ] Fallback comprovado por teste automatizado.
- [ ] `mvn verify` roda todos os testes localmente com apenas Docker instalado.
- [ ] CI verde na `main` e badge visível no README.
- [ ] Nenhum teste depende da API real do Gemini.

## Fora desta fase

Métricas em dashboard e RAG (fase 5).
