package br.com.doistecht.iaservice.client;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

	Optional<Client> findByApiKeyHash(String apiKeyHash);

	boolean existsByName(String name);

	List<Client> findAllByOrderByNameAsc();

}
