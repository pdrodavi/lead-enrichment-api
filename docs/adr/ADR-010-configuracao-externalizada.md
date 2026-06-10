# ADR-010: Configuração Externalizada com @ConfigurationProperties

## Status

Aceito

## Contexto

O código original continha diversos mapas e listas hardcoded nas classes de serviço, o que dificultava a manutenção e exigia recompilação para qualquer ajuste. Os principais casos eram:

- **TechScraperService**: Mapa com ~90 assinaturas HTML para detecção de tecnologias (`SOCIAL_SIGNATURES`, `ANALYTICS_SIGNATURES`, etc.)
- **SocialDiscoveryService**: Lista de 31 domínios de redes sociais (`SOCIAL_DOMAINS`) e mapa de nomes amigáveis (`PLATFORM_NAMES`)
- **OpenSerpSearch**: Constantes de timeout (`CONNECT_TIMEOUT_SECONDS`, `READ_TIMEOUT_SECONDS`)

Esses valores mudam com frequência (novas plataformas, alterações em assinaturas HTML) e não deveriam exigir recompilação.

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
    private Map<String, String> domains;         // plataforma → domínio
    private Map<String, String> platformNames;   // domínio → nome amigável
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;
}
```

```yaml
social-discovery:
  domains:
    linkedin: "linkedin.com"
    github: "github.com"
    # ... 31 plataformas
  platform-names:
    linkedin.com: "LinkedIn"
    github.com: "GitHub"
    # ... 31 nomes
```

#### AppConfig (RestTemplate Bean)

```java
@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(Duration.ofSeconds(5));
            setReadTimeout(Duration.ofSeconds(20));
        }});
    }
}
```

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
