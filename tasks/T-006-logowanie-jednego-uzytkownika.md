---
id: T-006
epic: E0
title: Logowanie jednego użytkownika
status: todo
depends_on: [T-005]
manual_review: true
---

## Cel
Aplikacja będzie publicznie dostępna, więc bez zalogowania nie może pokazywać żadnych danych.

## Zakres
- Spring Security, jedno konto, login i hash hasła ze zmiennych środowiskowych
- logowanie formularzem, sesja w ciasteczku (`HttpOnly`, `Secure` na produkcji, `SameSite`)
- ochrona CSRF dla żądań modyfikujących
- ekran logowania i wylogowanie we frontendzie
- bez logowania dostępne są tylko: healthcheck, ekran logowania i zasoby statyczne
- aplikacja nie startuje bez ustawionych danych logowania
- nazwy zmiennych w `.env.example`, sposób wygenerowania hasha w `docs/DEPLOY.md`

## Poza zakresem
- rejestracja, reset hasła, wielu użytkowników, role

## Kryteria akceptacji
- [ ] żądanie do `/api/**` bez sesji zwraca 401
- [ ] poprawne dane logują, błędne zwracają 401 bez informacji, które pole było złe
- [ ] po wylogowaniu sesja jest nieważna
- [ ] hasło nie występuje w repozytorium ani w logach

## Testy
- testy integracyjne: dostęp bez sesji, poprawne i błędne logowanie, wylogowanie
- test frontendu: przekierowanie na ekran logowania przy odpowiedzi 401

## Blokada
