package br.com.doistecht.iaservice.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsageService {

	static final int MAX_PERIOD_DAYS = 366;

	private final UsageRecordRepository repository;

	public UsageService(UsageRecordRepository repository) {
		this.repository = repository;
	}

	/** Período de consulta em dias do calendário UTC, com início e fim inclusivos. */
	public record Period(LocalDate from, LocalDate to) {

		public Period {
			if (from.isAfter(to)) {
				throw new InvalidPeriodException("A data inicial deve ser anterior ou igual à final.");
			}
			if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
				throw new InvalidPeriodException("O período máximo é de %d dias.".formatted(MAX_PERIOD_DAYS));
			}
		}

		/** Últimos 30 dias, incluindo hoje, quando as datas não são informadas. */
		public static Period of(LocalDate from, LocalDate to) {
			LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
			return new Period(from == null ? end.minusDays(29) : from, end);
		}

		Instant startInclusive() {
			return from.atStartOfDay(ZoneOffset.UTC).toInstant();
		}

		Instant endExclusive() {
			return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		}

	}

	public UsageTotals totalsForClient(Long clientId, Period period) {
		return repository.totalsForClient(clientId, period.startInclusive(), period.endExclusive());
	}

	public List<ClientUsage> totalsByClient(Period period) {
		return repository.totalsByClient(period.startInclusive(), period.endExclusive());
	}

}
