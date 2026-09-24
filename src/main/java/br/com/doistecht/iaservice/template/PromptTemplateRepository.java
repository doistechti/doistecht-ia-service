package br.com.doistecht.iaservice.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

	Optional<PromptTemplate> findFirstByNameAndActiveTrueOrderByVersionDesc(String name);

	Optional<PromptTemplate> findByNameAndVersion(String name, int version);

	Optional<PromptTemplate> findFirstByNameOrderByVersionDesc(String name);

	List<PromptTemplate> findByNameOrderByVersionDesc(String name);

	List<PromptTemplate> findAllByOrderByNameAscVersionDesc();

}
