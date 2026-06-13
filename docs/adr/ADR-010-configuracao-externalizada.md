# ADR-010: Configuração Externalizada com @ConfigurationProperties

## Status

Aceito

## Contexto

O código original continha diversos mapas e listas hardcoded nas classes de serviço, o que dificultava a manutenção e exigia recompilação para qualquer ajuste. Os principais casos eram:

- **TechScraperService**: Mapa com ~90 assinaturas HTML para detecção de tecnologias (`SOCIAL_SIGNATURES`, `ANALYTICS_SIGNATURES`, etc.)
- **SocialDiscoveryService**: Lista de 31 domínios de redes sociais (`SOCIAL_DOMAINS`) e mapa de nomes amigáveis (`PLATFORM_NAMES`)
Esses valores mudam com frequência (novas plataformas, alterações em assinaturas HTML) e não deveriam exigir recompilação.

### Refatoração Posterior: Externalização de Secrets

Em rodadas posteriores de refatoração, as credenciais e senhas (API_KEY, ENCRYPTION_SECRET, DB_PASSWORD) também foram externalizadas para variáveis de ambiente, eliminando fallbacks hardcoded do `application.yml`. Agora são lidas exclusivamente de:

- **`.env`** (desenvolvimento local, carregado pelo `run.bat`)
- **Variáveis de ambiente do sistema** (produção/Docker)

```yaml
# application.yml (sem fallbacks)
api:
  key: ${API_KEY}
  encryption:
    secret: ${ENCRYPTION_SECRET}
```

## Decisão

Externalizar todas as configurações de serviços para o `application.yml` utilizando o mecanismo `@ConfigurationProperties` do Spring Boot.

### Classes de Propriedades

#### TechScraperProperties

```java
@ConfigurationProperties(prefix = "techscraper")
public class TechScraperProperties {
    private Map<String, String> signatures;  // tecnologia → assinatura HTML
}
```

```yaml
techscraper:
  signatures:
    "React": "id=\"__next\""
    "Shopify": "Shopify.shop"
    "WordPress": "/wp-content/"
    # ... ~90 assinaturas
```

#### SocialDiscoveryProperties

```java
@ConfigurationProperties(prefix = "social-discovery")
public class SocialDiscoveryProperties {
    private List<String> socialDomains = List.of();         // lista de domínios
    private Map<String, String> platformNames = Map.of();   // domínio → nome amigável
}
```

```yaml
social-discovery:
  social-domains:
    - linkedin.com
    - github.com
    # ... 33 plataformas
  platform-names:
    linkedin.com: "LinkedIn"
    github.com: "GitHub"
    # ... 33 nomes
```

#### AppConfig (RestTemplate Beans)

```java
@Configuration
public class AppConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    @Qualifier("openSerpRestTemplate")
    public RestTemplate openSerpRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
    }
}
```

> Foram configurados **dois** beans de `RestTemplate`: um padrão (timeouts 5s/20s) e um dedicado ao OpenSERP (timeouts 10s/90s), já que buscas no Google self-hosted podem levar mais de 30 segundos.

### Ativação

```java
@SpringBootApplication
@EnableConfigurationProperties({
    TechScraperProperties.class,
    SocialDiscoveryProperties.class
})
public class LeadEnrichmentApplication { ... }
```

### Migração Realizada

| Antes (hardcoded) | Depois (externalizado) | Arquivo |
|---|---|---|
| `SOCIAL_SIGNATURES`, `ANALYTICS_SIGNATURES`, etc. | `techscraper.signatures` | `application.yml` |
| `SOCIAL_DOMAINS` | `social-discovery.domains` | `application.yml` |
| `PLATFORM_NAMES` | `social-discovery.platform-names` | `application.yml` |
| OkHttp timeouts | `RestTemplate (connectTimeout: 5s, readTimeout: 20s)` | `AppConfig.java` |

## Consequências

- Positivas:
  - Ajustes em assinaturas e plataformas sem recompilar — apenas reiniciar
  - Facilidade de configurar diferentes valores por ambiente (dev/staging/prod)
  - Código mais limpo: serviços não carregam dados de configuração
  - Validação na inicialização: propriedades obrigatórias causam falha precoce
  - IDE oferece autocomplete para propriedades com `spring-boot-configuration-processor`

- Negativas:
  - Mais arquivos de configuração para gerenciar
  - Erro de digitação em YAML só é detectado em runtime
  - Necessário reiniciar a aplicação para refletir alterações
  - Mapa de assinaturas grande (~90 entradas) torna o YAML extenso

## Alternativas Consideradas

| Alternativa | Motivo da Rejeição |
|---|---|
| Manter hardcoded | Exige recompilação para qualquer ajuste |
| Banco de dados | Complexidade desnecessária; dados mudam com baixa frequência |
| Arquivo properties separado | YAML com mapas aninhados é mais legível que .properties |
| Variáveis de ambiente individuais | Inviável para 90+ assinaturas |

## Referências

- [Spring Boot @ConfigurationProperties](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot YAML Syntax](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [RestTemplate (Spring)](https://docs.spring.io/spring-framework/reference/integration/rest-client-access.html)
