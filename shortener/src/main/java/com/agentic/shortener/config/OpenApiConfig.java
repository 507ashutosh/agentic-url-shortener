package com.agentic.shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Title and description shown in Swagger UI (/swagger-ui.html) and the spec (/v3/api-docs). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI shortenerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("URL Shortener API")
                .version("0.1.0")
                .description("""
                        Create short links, follow them, and read click statistics.
                        Errors use RFC 9457 problem details. Creating links is rate limited per client IP."""));
    }
}
