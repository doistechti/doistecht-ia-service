package br.com.doistecht.iaservice.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	List<Document> findByClientIdOrderByCreatedAtDesc(Long clientId);

	Optional<Document> findByIdAndClientId(Long id, Long clientId);

}
