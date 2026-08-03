package com.doubleledger.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String POST_TRANSACTION_EXAMPLE = """
            {
              "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
              "description": "transfer wallet to pool",
              "legs": [
                {
                  "accountId": "11111111-1111-1111-1111-111111111111",
                  "amountMinorUnits": 500,
                  "direction": "CREDIT"
                },
                {
                  "accountId": "22222222-2222-2222-2222-222222222222",
                  "amountMinorUnits": 500,
                  "direction": "DEBIT"
                }
              ]
            }
            """;

    @Bean
    OpenAPI ledgerOpenApi(@Value("${server.port:8080}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("DoubleLedger API")
                        .version("v1"))
                .servers(List.of(new Server().url("http://localhost:" + serverPort).description("Local")));
    }

    @Bean
    OpenApiCustomizer postTransactionTryItOutExample(ObjectMapper objectMapper) {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> schema = openApi.getComponents().getSchemas().get("PostTransactionRequest");
            if (schema == null) {
                return;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> example = objectMapper.readValue(POST_TRANSACTION_EXAMPLE, Map.class);
                schema.setExample(example);
            } catch (Exception ignored) {
                // OpenAPI still works; Swagger may fall back to single-leg sample
            }
        };
    }
}
