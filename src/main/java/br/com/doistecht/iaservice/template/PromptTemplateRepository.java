package br.com.doistecht.iaservice.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Nas consultas com {@code clientId}, o valor {@code null} seleciona os templates globais
 * (o Spring Data traduz para {@code client_id IS NULL}).
 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

	Optional<PromptTemplate> findFirstByClientIdAndNameAndActiveTrueOrderByVersionDesc(Long clientId, String name);

	Optional<PromptTemplate> findByClientIdAndNameAndVersion(Long clientId, String name, int version);

	Optional<PromptTemplate> findFirstByClientIdAndNameOrderByVersionDesc(Long clientId, String name);

	List<PromptTemplate> findByClientIdAndNameOrderByVersionDesc(Long clientId, String name);

	boolean existsByClientIdAndName(Long clientId, String name);

	List<PromptTemplate> findAllByOrderByNameAscClientIdAscVersionDesc();

}
