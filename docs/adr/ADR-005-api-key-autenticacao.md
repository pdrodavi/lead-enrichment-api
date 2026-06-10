# ADR-005: Autenticação via API Key com Servlet Filter

## Status

Aceito

## Contexto

A API precisa de um mecanismo de autenticação simples e eficaz para proteger endpoints REST. Requisitos:

- Autenticação stateless (sem sessão ou JWT complexo)
- Baixa latência — sem consulta a banco de dados
- Fácil de usar em integrações (curl, Postman, sistemas terceiros)
- Endpoints públicos específicos (health check, Swagger)
- Resposta JSON padronizada em caso de erro

## Decisão

Implementar um **Servlet Filter** (`OncePerRequestFilter`) que valida uma **API Key** enviada via header HTTP `X-API-KEY`.

### Implementação

```java
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public ApiKeyFilter(@Value("${api.key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-resources");
    }

    @Override
    protected void doFilterInternal(...) {
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            response.setStatus(401);
            response.getWriter().write("{\"error\":\"API Key ausente ou inválida\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
```

### Endpoints Públicos

| Path | Finalidade |
|---|---|
| `/actuator/**` | Health checks, métricas, probes |
| `/swagger-ui/**` | Documentação interativa |
| `/v3/api-docs/**` | OpenAPI spec |
| `/swagger-resources/**` | Recursos Swagger |

## Consequências

- Positivas:
  - Zero overhead de banco de dados (comparação em memória)
  - Simples de implementar e consumir
  - Stateless — sem sessão ou cookie
  - Fácil de rotear em API Gateways (enviam X-API-KEY direto)

- Negativas:
  - Chave estática em configuração — sem revogação individual
  - Não escala para múltiplos tenants sem lógica adicional
  - Chave visível em logs se não mascarada

## Alternativas Consideradas

| Alternativa | Motivo da Rejeição |
|---|---|
| JWT / OAuth2 | Complexidade excessiva para uma API interna; sem necessidade de refresh tokens |
| Basic Auth | Usuário/senha expostos em texto plano, menos seguro que API Key |
| Spring Security | Overhead de configuração para uma regra simples de filtro |

## Referências

- [OncePerRequestFilter (Spring)](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html)
- [OWASP API Key](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)
