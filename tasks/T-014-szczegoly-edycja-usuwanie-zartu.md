---
id: T-014
epic: E1
title: Szczegóły, edycja treści i usuwanie żartu z kontrolą wersji
status: todo
depends_on: [T-013]
manual_review: true
---

## Cel
Umożliwić odczyt szczegółów pojedynczego żartu oraz edycję treści i usunięcie z optymistyczną kontrolą współbieżności, tak aby równoległe zmiany nie nadpisywały się po cichu.

## Zakres
- `GET /api/jokes/{id}` → `JokeDetail` (`id`, `content`, `createdAt`, `updatedAt`, `version`); DTO rozszerzalny, kolejne taski dołożą status, tagi i alternatywy
- `PUT /api/jokes/{id} {content, version}` → edycja treści oryginału; zmienia wyłącznie treść i podbija `version`
- `DELETE /api/jokes/{id}` z wersją (nagłówek `If-Match` albo pole `version`) → usuwa żart
- optymistyczna blokada: niezgodność wersji → `409` `ProblemDetail`; nieistniejące `id` → `404` `ProblemDetail`
- frontend: widok szczegółów, edycja treści i usuwanie (mobile-first) przez moduł klienta API; konflikt wersji pokazany komunikatem po polsku

## Poza zakresem
- statusy (T-015), tagi (T-016), filtry i paginacja (T-017)
- puenty alternatywne (T-018, T-019)

## Kryteria akceptacji
- [ ] `GET /api/jokes/{id}` zwraca szczegóły istniejącego żartu, a dla nieistniejącego `404` `ProblemDetail`
- [ ] `PUT` z aktualną wersją zapisuje nową treść i podbija `version`
- [ ] `PUT` lub `DELETE` z nieaktualną wersją zwraca `409` `ProblemDetail` i nie zmienia danych
- [ ] `DELETE` z aktualną wersją usuwa żart (kolejny `GET` → `404`)
- [ ] edycja zmienia wyłącznie treść; pozostałe pola żartu pozostają bez zmian
- [ ] operacje modyfikujące wymagają sesji (`401` bez niej) i są chronione CSRF

## Testy
- IT (`*IT`): odczyt szczegółów; `404` dla nieistniejącego; edycja z wersją; `409` przy konflikcie; usunięcie i `404` po nim
- IT: dwie edycje tego samego żartu — druga z nieaktualną wersją dostaje `409`, dane bez zmian
- frontend (Vitest + RTL): szczegóły renderują dane z klienta API; konflikt wersji pokazuje komunikat; usuwanie wywołuje klienta API

## Blokada
