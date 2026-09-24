package br.com.doistecht.iaservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async}. Com virtual threads ativas, o Spring Boot usa um
 * executor que cria uma virtual thread por tarefa.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
