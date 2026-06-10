# ADR-002: Banco de Dados — PostgreSQL 16 + Spring Data JPA com ddl-auto=update

## Status

Aceito

## Contexto

A aplicação precisa persistir leads enriquecidos com uma quantidade variável de campos (DNS, tecnologias, redes sociais, RDAP). Os requisitos incluem:

- Armazenamento de campos com muitos-itens (listas de registros DNS, tecnologias, URLs)
- Consulta por e-mail (hash SHA-256), nome, domínio e status
- Suporte a soft delete com campo deletedAt
- Capacidade de armazenar JSON bruto do RDAP e OpenSERP (campos TEXT)
- Foreign Key e constraints de unicidade

## Decisão

Utilizar **PostgreSQL 16** como banco de dados relacional com **Spring Data JPA** e **Hibernate 6.x** como ORM.

### Estratégia de DDL: `ddl-auto: update`

O Hibernate cria e atualiza automaticamente as tabelas com base nas entidades JPA:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

### Mapeamento

| Padrão JPA | Uso |
|---|---|
| `@ElementCollection(fetch = LAZY)` | Listas de DNS, tecnologias, sociais, URLs |
| `@Column(columnDefinition = "TEXT")` | JSON bruto RDAP e OpenSERP |
| `@Column(length = 64, unique = true)` | Hash SHA-256 do e-mail |
| `@Convert(converter = EncryptedEmailConverter.class)` | E-mail criptografado |
| `@GeneratedValue(strategy = IDENTITY)` | ID auto-incremento |
| `@PreUpdate` / `updatedAt` | Populado automaticamente em cada atualização |

### Consultas

```java
public interface LeadRepository extends JpaRepository<Lead, Long> {
    Optional<Lead> findByEmailHash(String emailHash);        // Unique — lookup por email
    Optional<Lead> findByName(String name);                   // Busca por nome
    List<Lead> findByStatus(String status);                   // ACTIVE ou DELETED
    List<Lead> findByDomainAndStatus(String domain, String status); // Leads de um domínio
}
```

## Consequências

- Positivas:
  - PostgreSQL 16 é maduro, performático e amplamente suportado
  - `ddl-auto: update` acelera o desenvolvimento sem migrations manuais
  - `@ElementCollection` simplifica listas sem tabelas join explícitas
  - Spring Data JPA elimina SQL boilerplate

- Negativas:
  - `ddl-auto: update` não é seguro para produção sem revisão
  - `@ElementCollection(fetch = LAZY)` exige acesso dentro de transação ativa
  - Dependência de PostgreSQL impede uso de bancos embarcados (H2) em produção

## Referências

- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [PostgreSQL 16](https://www.postgresql.org/docs/16/release-16.html)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.3/userguide/html_single/Hibernate_User_Guide.html)
