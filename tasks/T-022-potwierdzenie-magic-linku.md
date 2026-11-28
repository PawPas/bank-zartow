---
id: T-022
epic: E-ML
title: Potwierdzenie magic linku — atomowe zużycie i bezpieczna sesja
status: todo
depends_on: [T-020]
manual_review: true
---

## Cel
Jawny POST zamienia ważny token na dokładnie jedną sesję — tę samą, której używa
logowanie hasłem — bez podatności na fixation i bez skutków ubocznych GET-ów.

## Zakres
- `POST /api/auth/magic-link/confirm` (publiczny, CSRF): zużycie tokenu serwisem
  z T-020; sukces → ustanowienie sesji, `SecurityContext` jedynego użytkownika
  utrwalony właściwą strategią sesyjną Spring Security, unieważnienie pozostałych
  aktywnych tokenów (operacja z T-020, polityka D-ML6), odpowiedź `204`;
  porażka → `401` z generycznym `ProblemDetail` identycznym dla wszystkich przyczyn
  (nieistniejący/zużyty/wygasły/unieważniony)
- ustanowienie sesji z jawnym rozdzieleniem dwóch przypadków: przy **istniejącej**
  `HttpSession` (np. anonimowej) rotacja ID (`HttpServletRequest#changeSessionId`);
  przy **braku** sesji (nowa przeglądarka — publiczny `GET /api/auth/session`
  dostarcza ciasteczko CSRF, ale nie tworzy `HttpSession`, a samo `changeSessionId`
  sesji nie utworzy) jawne utworzenie nowej sesji; sekwencja zaprojektowana tak,
  by zużycie tokenu nie mogło zakończyć się wyjątkiem braku sesji już po zużyciu
- bramka funkcji: endpoint aktywny wyłącznie przy otwartej bramce `MagicLinkGate`
  z T-020 (komplet zmiennych + zdolności `wysylka` i `limiter`); przy zamkniętej
  bramce 404 — **także wtedy, gdy w bazie istnieje ważny token**, i także po
  usunięciu konfiguracji dostawcy (rollback)
- `Referrer-Policy: same-origin` dla odpowiedzi aplikacji
- żadnych zmian w `/api/auth/login|logout|session|me`

Prerequisity tego taska dostarcza w całości T-020 (operacje serwisu, kompletna
bramka `MagicLinkGate` — w konfiguracji testowej zdolności `wysylka` i `limiter`
zgłasza się jawnie, bez portu mailera i bez limitera — oraz `Clock`). Task nie
zależy od T-021 i nie korzysta z portu mailera — tokeny w testach wydaje się
bezpośrednio serwisem z T-020.

## Poza zakresem
- żądanie linku i wysyłka (T-021); frontend (T-023); rate limiting (T-024);
  usuwanie logowania hasłem (decyzja D-ML5)

## Kryteria akceptacji
- [ ] poprawny token + CSRF → 204, sesja działa na chronionym `/api/**`
      (zweryfikowane na istniejących endpointach, np. `GET /api/auth/me` lub
      `GET /api/jokes` — oba istnieją na `main`)
- [ ] potwierdzenie przy wcześniejszej sesji anonimowej rotuje ID sesji —
      ciasteczko przed ≠ po (anty-fixation)
- [ ] potwierdzenie w przeglądarce bez istniejącej sesji (żądanie bez `JSESSIONID`)
      tworzy nową sesję i kończy się 204, a `SecurityContext` jest poprawnie
      utrwalony (kolejne żądanie `/api/**` w tej sesji jest uwierzytelnione);
      zużycie tokenu nie kończy się wyjątkiem braku sesji
- [ ] drugi POST tym samym tokenem → 401; dwa równoległe POST-y → dokładnie jedna
      sesja
- [ ] token wygasły/nieistniejący/unieważniony → identyczny generyczny 401 bez
      rozróżnienia przyczyny
- [ ] udane potwierdzenie unieważnia pozostałe aktywne tokeny (zgodnie z domyślną
      polityką D-ML6)
- [ ] żaden GET (w tym `/login/confirm` i `/api/auth/session`) nie zmienia stanu
      tokenu (I7)
- [ ] POST bez poprawnego nagłówka CSRF jest odrzucany i nie zużywa tokenu
- [ ] przy zamkniętej bramce endpoint zwraca 404; w szczególności konfiguracja
      produkcyjna z ustawionym adresem/URL, ale bez zdolności `wysylka` (brak
      realnego adaptera) → 404 również przy istniejącym ważnym tokenie w bazie,
      a token pozostaje nietknięty

## Testy
- IT (Testcontainers): pełny przepływ wydanie (serwisem z T-020) → potwierdzenie →
  dostęp do `/api/**`; dwa scenariusze sesji: z wcześniejszą sesją anonimową
  (rotacja ID, ciasteczka przed/po) i bez `JSESSIONID` (utworzenie nowej sesji,
  utrwalony `SecurityContext`); wyścig dwóch potwierdzeń; wygaśnięcie (przesunięcie
  `Clock`); ponowne użycie; unieważnienie pozostałych po sukcesie; GET bez skutków;
  CSRF; zamknięta bramka → 404 przy istniejącym ważnym tokenie (konfiguracja
  produkcyjna bez zdolności `wysylka`), token nietknięty; sesja z magic linku
  wylogowuje się istniejącym `POST /api/auth/logout`

## Blokada
Zniesiona 2026-11-28: właściciel zaakceptował `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1, „Zatwierdzam" na Telegramie po merge PR #17) i zlecił mechaniczny
zapis punktów A–C w `SCOPE.md` oraz odblokowanie T-020..T-024 (`blocked` → `todo`).
Zapis decyzji: nagłówek `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`.
