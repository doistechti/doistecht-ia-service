package br.com.doistecht.iaservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.ai.google.genai.api-key=test-gemini-key",
		"ia-service.api-key=test-api-key"
})
class IaServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
