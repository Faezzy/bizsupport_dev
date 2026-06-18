package ru.bizsupport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OpenApiConfig {

    @Bean
    public OpenAPI bizSupportOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BizSupport REST API")
                        .description("API информационной системы поддержки малого бизнеса. " +
                                "Включает модули: авторизация, профиль компании, налогообложение, " +
                                "калькулятор налогов, госзакупки, уведомления, избранное, правовые ссылки.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BizSupport Team")
                                .email("admin@bizsupport.ru")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))
                .components(new Components()
                        .addSecuritySchemes("Bearer JWT",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Введите JWT-токен, полученный через POST /api/auth/login")));
    }
}
