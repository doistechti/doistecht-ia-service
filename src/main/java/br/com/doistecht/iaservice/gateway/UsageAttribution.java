package br.com.doistecht.iaservice.gateway;

/**
 * Atribui a um cliente as chamadas feitas fora da requisição HTTP, como o processamento
 * de documentos em segundo plano. Sem isso, essas chamadas não seriam contabilizadas.
 */
public final class UsageAttribution {

	private static final ThreadLocal<CallContext> CURRENT = new ThreadLocal<>();

	private UsageAttribution() {
	}

	/**
	 * Executa a tarefa atribuindo ao cliente as chamadas ao modelo feitas dentro dela.
	 * O cache de respostas não é usado nessas chamadas.
	 */
	public static void runAs(Long clientId, String clientName, String endpoint, Runnable task) {
		CallContext previous = CURRENT.get();
		CURRENT.set(new CallContext(clientId, clientName, endpoint, true));
		try {
			task.run();
		}
		finally {
			if (previous == null) {
				CURRENT.remove();
			}
			else {
				CURRENT.set(previous);
			}
		}
	}

	static CallContext current() {
		return CURRENT.get();
	}

}
