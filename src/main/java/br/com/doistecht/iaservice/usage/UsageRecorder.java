package br.com.doistecht.iaservice.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Grava o uso de forma assíncrona, sem aumentar a latência da resposta ao cliente.
 * Uma falha ao gravar é registrada no log, mas nunca derruba a requisição.
 */
@Component
public class UsageRecorder {

	private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

	private final UsageRecordRepository repository;

	private final CostEstimator costEstimator;

	public UsageRecorder(UsageRecordRepository repository, CostEstimator costEstimator) {
		this.repository = repository;
		this.costEstimator = costEstimator;
	}

	@Async
	public void record(UsageEvent event) {
		try {
			repository.save(new UsageRecord(event, costEstimator.estimate(event.model(), event.usage())));
		}
		catch (RuntimeException ex) {
			log.warn("Não foi possível gravar o uso da chamada: {}", event, ex);
		}
	}

}
