package br.com.doistecht.iaservice.security.admin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ia-service.admin-access")
public record AdminAccessProperties(@DefaultValue("12h") Duration sessionTtl) {
}
