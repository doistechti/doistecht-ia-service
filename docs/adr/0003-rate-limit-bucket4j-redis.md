# ADR 0003 — Rate limit com Bucket4j + Redis e fail open

**Status:** aceita (fase 3, corrigida na fase 4)

## Contexto

Cada cliente precisa de um limite por minuto e de uma cota diária, válidos mesmo com várias instâncias do serviço. O plano gratuito do Gemini também tem limites baixos, então proteger a cota compartilhada é essencial.

## Decisão

- **Por minuto:** token bucket do Bucket4j, guardado no Redis. O limite faz parte da chave do bucket, então alterar o limite de um cliente cria um bucket novo.
- **Cota diária:** contador no Redis por dia do calendário (UTC), zerado à meia-noite.
- **Redis fora do ar:** *fail open*. O serviço continua respondendo, sem limites, e registra o problema no log. Cada operação no Redis tem timeout de 2 s.
- O Bucket4j usa um **cliente Redis próprio**, encerrado só no fim da aplicação.

## Alternativas consideradas

- **Fail closed** (recusar tudo sem Redis): mais seguro para a cota, mas derruba o serviço inteiro por causa de um componente auxiliar.
- **Janela móvel de 24 h para a cota:** mais precisa, mas mais difícil de explicar ao cliente do que "zera à meia-noite".

## Consequências

- Os limites funcionam com várias instâncias.
- Com o Redis fora do ar, cada requisição fica até ~4 s mais lenta (timeouts de rate limit e cache).
- Os testes da fase 4 revelaram que, reaproveitando a conexão do `LettuceConnectionFactory` do Spring, o Bucket4j ficava preso a uma conexão fechada depois de um restart e passava a liberar tudo. Daí o cliente próprio.
