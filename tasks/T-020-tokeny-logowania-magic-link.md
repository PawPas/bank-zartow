---
id: T-020
epic: E-ML
title: Tokeny logowania — model, pula, atomowe zużycie, bramka i konfiguracja
status: done
depends_on: [T-006]
manual_review: true
---

## Cel
Fundament magic linku: jednorazowe, wygasające tokeny przechowywane wyłącznie jako
hash, z ograniczoną pulą aktywnych tokenów, zużyciem atomowym także przy równoległych
żądaniach oraz wspólną, kompletną bramką funkcji, konfiguracją i zegarem dla
pozostałych tasków epiku — tak, żeby T-021 i T-022 mogły powstać niezależnie.

## Zakres
- migracja Flyway `V<kolejny>__login_token.sql` (tabela z `docs/EPIC-MAGIC-LINK.md` §4;
  numer to kolejny wolny dobrany przy implementacji)
- pakiet `auth`: encja/rekord, repozytorium, `LoginTokenService` z operacjami:
  - wydaj: 256-bit `SecureRandom`, base64url, zapis wyłącznie SHA-256; respektuje
    limit puli aktywnych tokenów (konfigurowalny, default 3); przy pełnej puli nie
    wydaje tokenu i sygnalizuje to wynikiem (bez wyjątku); **nie unieważnia**
    wcześniejszych niezużytych tokenów; egzekwowanie limitu odporne na wyścig
    równoległych wydań
  - zużyj: pojedynczy warunkowy UPDATE (wynik 1/0), dokładnie jeden zwycięzca
    w wyścigu
  - unieważnij pozostałe aktywne: operacja wołana po udanym logowaniu (użyje jej
    T-022; parametry polityki wg decyzji D-ML6, defaulty z `docs/EPIC-MAGIC-LINK.md` §6)
  - unieważnij wskazany token: operacja zwalniająca miejsce w puli (użyje jej T-021
    po pewnym błędzie wysyłki — polityka D-ML6)
- właściwości konfiguracyjne epiku (`APP_AUTH_EMAIL`, `APP_PUBLIC_URL`,
  `APP_MAGIC_LINK_TTL_MINUTES` — TTL default 15 minut w kodzie jako odwracalna
  propozycja, D-ML4)
- **wspólna bramka funkcji `MagicLinkGate`, domyślnie zamknięta** — jedyne źródło
  decyzji „czy endpointy magic linku są aktywne" dla T-021 i T-022: otwarta tylko
  przy komplecie zmiennych (`APP_AUTH_EMAIL`, `APP_PUBLIC_URL`) **i** zgłoszonych
  zdolnościach `wysylka` oraz `limiter`; zdolności to abstrakcyjne kontrybucje
  konfiguracji — bez odwołań do portu mailera, który powstaje dopiero w T-021;
  w konfiguracji produkcyjnej zdolności zgłoszą dopiero realny adapter (T-025)
  i limiter (T-024), w testowej/deweloperskiej zgłasza się je jawnie; brak
  zmiennych lub zdolności nie przeszkadza w starcie aplikacji
- wstrzykiwany `java.time.Clock` (bean) jako jedyne źródło czasu tokenów; testy
  sterują zegarem deterministycznie, bez sleepów
- sprzątanie wierszy wygasłych/zużytych/unieważnionych przy wydawaniu nowego tokenu
- brak endpointów HTTP w tym tasku

## Poza zakresem
- endpointy HTTP, mail, frontend, rate limiting (T-021..T-024)

## Kryteria akceptacji
- [ ] w bazie nigdy nie ląduje jawny token, wyłącznie hash (I1)
- [ ] zużycie tokenu zużytego, wygasłego, unieważnionego lub nieistniejącego zwraca
      odmowę bez zmiany stanu (I2)
- [ ] dwa równoległe zużycia tego samego tokenu dają dokładnie jeden sukces (I3)
- [ ] wydanie nowego tokenu nie unieważnia wcześniejszych niezużytych; liczba
      aktywnych tokenów nigdy nie przekracza skonfigurowanego limitu puli, także
      przy równoległych wydaniach (I4)
- [ ] przy pełnej puli wydanie zwraca wynik „nie wydano" bez wyjątku i bez zmiany
      stanu istniejących tokenów
- [ ] operacja „unieważnij pozostałe aktywne" unieważnia wszystkie aktywne tokeny
      poza wskazanym zużytym
- [ ] operacja „unieważnij wskazany token" unieważnia dokładnie ten token i zwalnia
      miejsce w puli
- [ ] bramka `MagicLinkGate` jest domyślnie zamknięta; brak dowolnej zmiennej lub
      dowolnej zdolności → zamknięta; komplet zmiennych i zdolności → otwarta
      (pokryte testami wszystkie brakujące elementy z osobna)
- [ ] token ani jego hash nie pojawiają się w logach serwisu (I5)
- [ ] TTL i wygasanie liczone wyłącznie z wstrzykiwanego `Clock`; test przesuwa zegar
      zamiast czekać

## Testy
- IT (Testcontainers, realny PostgreSQL): wydanie i zużycie; podwójne zużycie;
  zużycie po TTL (przesunięcie `Clock`); wyścig dwóch wątków o jeden token (dokładnie
  jeden zwycięzca); wyścig równoległych wydań przy prawie pełnej puli (limit nigdy
  nie przekroczony); pełna pula → brak wydania; unieważnienie pozostałych po zużyciu;
  unieważnienie wskazanego tokenu zwalnia miejsce w puli; sprzątanie starych wierszy;
  trwałość po niezależnym odczycie
- test jednostkowy: generacja tokenu (długość, base64url, źródło `SecureRandom`),
  hashowanie SHA-256; bramka `MagicLinkGate` — domyślnie zamknięta, każdy brakujący
  element (zmienna, zdolność `wysylka`, zdolność `limiter`) z osobna trzyma ją
  zamkniętą, komplet otwiera

## Blokada
Zniesiona 2026-11-28: właściciel zaakceptował `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1, „Zatwierdzam" na Telegramie po merge PR #17) i zlecił mechaniczny
zapis punktów A–C w `SCOPE.md` oraz odblokowanie T-020..T-024 (`blocked` → `todo`).
Zapis decyzji: nagłówek `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`.
