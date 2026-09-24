package br.com.doistecht.iaservice.usage;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

	@Query("""
			select new br.com.doistecht.iaservice.usage.UsageTotals(
			    count(u),
			    sum(case when u.success = true then 1L else 0L end),
			    sum(case when u.cacheHit = true then 1L else 0L end),
			    sum(u.promptTokens),
			    sum(u.outputTokens),
			    sum(u.estimatedCost))
			from UsageRecord u
			where u.clientId = :clientId and u.createdAt >= :from and u.createdAt < :to
			""")
	UsageTotals totalsForClient(@Param("clientId") Long clientId, @Param("from") Instant from,
			@Param("to") Instant to);

	@Query("""
			select new br.com.doistecht.iaservice.usage.ClientUsage(
			    c.id,
			    c.name,
			    new br.com.doistecht.iaservice.usage.UsageTotals(
			        count(u),
			        sum(case when u.success = true then 1L else 0L end),
			        sum(case when u.cacheHit = true then 1L else 0L end),
			        sum(u.promptTokens),
			        sum(u.outputTokens),
			        sum(u.estimatedCost)))
			from UsageRecord u join Client c on c.id = u.clientId
			where u.createdAt >= :from and u.createdAt < :to
			group by c.id, c.name
			order by count(u) desc
			""")
	List<ClientUsage> totalsByClient(@Param("from") Instant from, @Param("to") Instant to);

	List<UsageRecord> findByClientIdOrderByCreatedAtDesc(Long clientId);

}
