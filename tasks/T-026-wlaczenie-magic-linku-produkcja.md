---
id: T-026
epic: E-ML
title: Produkcyjne włączenie magic linku i los logowania hasłem
status: blocked
depends_on: [T-023, T-024, T-025]
manual_review: true
---

## Cel
Domknięcie epiku: magic link działa pod publicznym adresem, dokumentacja kompletna,
a o logowaniu hasłem decyduje właściciel na podstawie działającej funkcji.

## Zakres
- `docs/DEPLOY.md`: pełna lista zmiennych magic linku i kolejność kroków właściciela
- weryfikacja na produkcji (wykonuje właściciel): żądanie → mail → potwierdzenie →
  sesja
- realizacja decyzji D-ML5 w wariancie wybranym przez właściciela; dopóki decyzja
  nie zapadła albo brzmi „zostaw hasło", wymóg `APP_AUTH_PASSWORD_HASH` i fail-closed
  start pozostają nietknięte
- uczciwy opis rollbacku w `docs/DEPLOY.md`: wyłączenie funkcji przez usunięcie
  zmiennych środowiskowych; powrót do logowania wyłącznie hasłem istnieje tylko przy
  skonfigurowanym `APP_AUTH_PASSWORD_HASH` — bez obietnicy automatycznego powrotu
  do hasła, gdyby D-ML5 je wcześniej usunęła

## Poza zakresem
- ustawianie zmiennych na Railway (właściciel); naprawa niezależnych problemów
  wdrożenia

## Kryteria akceptacji
- [ ] po krokach z `docs/DEPLOY.md` logowanie magic linkiem działa pod adresem
      Railway (potwierdza właściciel)
- [ ] bez zalogowania publiczny adres nadal nie pokazuje żadnych danych
- [ ] repo nie zawiera wartości sekretów
- [ ] zachowanie zgodne z decyzją D-ML5; fail-closed start nie jest usunięty przed
      tą decyzją
- [ ] `docs/DEPLOY.md` opisuje rollback bez obiecywania powrotu do hasła przy
      nieskonfigurowanym `APP_AUTH_PASSWORD_HASH`

## Testy
- IT: start aplikacji z kompletem zmiennych w formacie produkcyjnym (wzorzec jak
  w teście konfiguracji środowiskowej z T-007)

## Blokada
Trzy niezależne blokady:
1. Bramka zakresu: akceptacja `docs/MAGIC-LINK-SCOPE-PROPOSAL.md` (D-ML1).
2. Decyzja D-ML5 (los logowania hasłem) — podejmowana dopiero, gdy magic link
   realnie działa.
3. Działające (zielone) wdrożenie Railway: finalizacja T-007 i zmienne ustawione
   przez właściciela — obecny deploy `main` crashuje przy starcie z braku
   `APP_AUTH_PASSWORD_HASH` (stan opisany w `docs/EPIC-MAGIC-LINK.md` §2); ten epik
   tego nie naprawia.
Status na `todo` zmienia właściciel po wszystkich trzech rozstrzygnięciach oraz po
merge T-023..T-025.
