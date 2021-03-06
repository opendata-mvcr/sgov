package com.github.sgov.server.config;

import com.github.sgov.server.config.conf.ApplicationConf;
import cz.cvut.kbss.jopa.sessions.UnitOfWorkImpl;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.Contact;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.service.SecurityScheme;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.SecurityConfiguration;
import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

@Configuration
@SuppressWarnings("checkstyle:MissingJavadocType")
public class SpringFoxConfig {

    private final ApplicationConf applicationConf;

    @Autowired
    public SpringFoxConfig(ApplicationConf applicationConf) {
        this.applicationConf = applicationConf;
    }

    /**
     * Returns Swagger Docket.
     */
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .pathMapping("/")
            .select()
            .paths(PathSelectors.any())
            .build()
            .enableUrlTemplating(true)
            .genericModelSubstitutes(ResponseEntity.class)
            .ignoredParameterTypes(UnitOfWorkImpl.class)
            .apiInfo(apiInfo())
            .securityContexts(Collections.singletonList(securityContext()))
            .securitySchemes(Collections.singletonList(securityScheme()));
    }

    @Bean
    public SecurityScheme securityScheme() {
        return new ApiKey("Bearer", "Authorization", "header");
    }

    @Bean
    SecurityConfiguration security() {
        return SecurityConfigurationBuilder.builder()
            .scopeSeparator(",")
            .additionalQueryStringParams(null)
            .useBasicAuthenticationWithAccessCodeGrant(false)
            .build();
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder().securityReferences(defaultAuth())
            .operationSelector((each) -> true).build();
    }

    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope(
            "global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Collections.singletonList(new SecurityReference("Bearer",
            authorizationScopes));
    }

    ApiInfo apiInfo() {
        return new ApiInfoBuilder()
            .title("SGoV Server")
            .description("Server for Semantic Government Vocabulary (SGoV) management.")
            .version(applicationConf.getVersion())
            .contact(new Contact("Petr Křemen", "", "petr.kremen@mvcr.cz"))
            .build();
    }
}
