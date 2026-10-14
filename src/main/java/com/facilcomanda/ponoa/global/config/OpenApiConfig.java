package com.facilcomanda.ponoa.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "FacilComanda Ponoa Platform API",
            version = "v1",
            description =
                "API Backend de Alta Concurrencia para Gestión de Restaurantes, Facturación Electrónica SUNAT, KDS y POS.",
            contact =
                @Contact(
                    name = "Equipo de Desarrollo FacilComanda",
                    email = "soporte@facilcomanda.com"),
            license = @License(name = "Proprietary", url = "https://facilcomanda.com")),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Ingrese su token JWT de Keycloak")
public class OpenApiConfig {}
