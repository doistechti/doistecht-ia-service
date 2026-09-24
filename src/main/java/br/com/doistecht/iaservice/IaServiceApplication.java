package br.com.doistecht.iaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IaServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IaServiceApplication.class, args);
	}

}
