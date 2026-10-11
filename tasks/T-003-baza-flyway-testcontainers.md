---
id: T-003
epic: E0
title: Baza danych, Flyway i testy integracyjne
status: todo
depends_on: [T-002]
manual_review: true
---

## Cel
Prawdziwy PostgreSQL lokalnie i w testach, ze schematem zarządzanym wyłącznie przez migracje.

## Zakres
- `docker-compose.yml` z usługą PostgreSQL do pracy lokalnej
- Spring Data JPA i Flyway, `ddl-auto=validate`
- pierwsza migracja `V1__init.sql` (może tworzyć tylko tabelę techniczną)
- konfiguracja połączenia ze zmiennych środowiskowych, nazwy dopisane do `.env.example`
- Testcontainers dla testów `*IT`, uruchamiane przez `make test`, pomijane przez `make test-fast`

## Poza zakresem
- tabele domenowe (żarty, tagi), to epik 1

## Kryteria akceptacji
- [ ] `make test` uruchamia test integracyjny na PostgreSQL z Testcontainers i przechodzi w CI
- [ ] `make test-fast` nie wymaga Dockera
- [ ] aplikacja nie startuje, gdy schemat nie zgadza się z encjami

## Testy
- test integracyjny potwierdzający, że migracje wykonały się na pustej bazie

## Blokada
