# ADR 0004 — Uma única camada de retry

**Status:** aceita (fase 4, ampliada na fase 6)

## Contexto

Ao adicionar o Resilience4j, a investigação mostrou que já existiam duas camadas de retry escondidas:

- o **SDK do Google** repete sozinho até 5 vezes as chamadas que falham com 408/429/5xx, com espera de até 60 s, e **não tem timeout** por padrão;
- o **Spring AI** tem retry próprio, de até 10 tentativas para alguns tipos de erro.

Empilhadas, uma falha poderia virar dezenas de chamadas, e uma chamada travada ficaria presa para sempre.

## Decisão

- Desligar o retry do SDK (`HttpRetryOptions.attempts(1)`) e do Spring AI (`spring.ai.retry.max-attempts: 0`).
- Definir timeout no cliente HTTP de cada provedor (Gemini e Ollama).
- Manter uma única camada, o `ModelFallbackExecutor` (Resilience4j), que para cada modelo aplica retry apenas para erros transitórios, circuit breaker e, por fim, o modelo reserva.
- No streaming, trocar para o reserva só antes do primeiro trecho.

## Alternativas consideradas

- **Anotações do Resilience4j** (`@Retry`, `@CircuitBreaker`): exigiriam nomes fixos e métodos de fallback separados; aqui cada modelo tem seu circuit breaker, criado dinamicamente.
- **`TimeLimiter` do Resilience4j:** exigiria uma thread extra por chamada; o timeout no cliente HTTP cancela a chamada de verdade.

## Consequências

- O comportamento em falhas é previsível e configurável em um lugar só.
- Erros permanentes (ex.: `400`) não são repetidos, não abrem o circuito e não acionam o reserva, porque falhariam do mesmo jeito.
- Na validação com a API real, o `gemini-3.8-flash` respondeu `503` (alta demanda) com frequência. O streaming, que na fase 4 não tinha reserva, falhava sempre que isso acontecia; a regra "reserva antes do primeiro trecho" resolveu.
- Pior caso: se o modelo principal e o reserva travarem, a resposta pode demorar alguns minutos até o `503`. O circuit breaker passa a responder na hora depois de falhas repetidas.
