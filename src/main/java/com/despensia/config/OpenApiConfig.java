package com.despensia.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 configuration for Swagger UI documentation.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configure the custom OpenAPI metadata displayed in Swagger UI.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Despensia Backend API")
                .version("0.0.1-SNAPSHOT")
                .description("Modular monolith for grocery expiration tracking with AI-powered scanning.")
                .contact(new Contact().name("Despensia Team")));
    }
}
