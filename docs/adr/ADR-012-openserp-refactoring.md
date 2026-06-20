# ADR-012: Extração de Componentes do OpenSerpSearchService

## Status

**Aprovado (Jun/2026)**

## Contexto

O `OpenSerpSearchService` acumulava 9 responsabilidades distintas em ~780 linhas:
orquestração de cache L1+L2, circuit breaker, rate limiting, proxy rotation, failover,
retry com backoff, parsing de resposta (JSON + texto), ContentTracker e 6 builders
de query de busca.

4 dos 6 métodos de busca copiavam o mesmo padrão de cache L1+L2 em vez de usar o
método `getOrFetch()` compartilhado, resultando em ~100 linhas de código duplicado.

## Decisão

Extrair 3 classes especializadas e refatorar o service para usar composição:

### OpenSerpCircuitBreaker

```java
@Component
public class OpenSerpCircuitBreaker {
    public boolean isOpen() { ... }
    public boolean recordCaptcha() { ... }
    public void reset() { ... }
}
```
- Abre o circuito após 3 CAPTCHAs consecutivos
- Cooldown de 5 minutos
- Reset automático após expiração do cooldown

### OpenSerpResponseParser

```java
@Component
public class OpenSerpResponseParser {
    public JsonArray parse(String raw, String label) { ... }
}
```
- Parse de JSON (formato estruturado padrão)
- Fallback para formato texto/table (legado)
- Regex substituída por parser iterativo linha-a-linha

### OpenSerpRateLimiter

```java
@Component
public class OpenSerpRateLimiter {
    public void acquire() { ... }
}
```
- Delay mínimo de 2s entre requisições
- Usa `AtomicReference<Instant>` thread-safe

### Refatoração do OpenSerpSearchService

- `searchSocialMedia()`, `searchProfessional()`, `searchContact()`, `searchNews()`
  refatorados para usar `getOrFetch()` — eliminou ~200 linhas de código duplicado
- Classe reduzida de ~780 para ~450 linhas
- Testes atualizados com mocks das 3 novas classes

## Consequências

### Positivas
- **SRP**: Cada classe tem responsabilidade única
- **Testabilidade**: Componentes podem ser testados isoladamente
- **DRY**: Código de cache centralizado em `getOrFetch()`
- **Manutenibilidade**: Classe principal reduzida em 42%

### Negativas
- Aumento de 1 para 4 classes (+3 arquivos)
- Testes existentes precisaram de mocks adicionais
