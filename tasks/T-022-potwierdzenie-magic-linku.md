---
id: T-022
epic: E-ML
title: Potwierdzenie magic linku — atomowe zużycie i bezpieczna sesja
status: blocked
depends_on: [T-020]
manual_review: true
---

## Cel
Jawny POST zamienia ważny token na dokładnie jedną sesję — tę samą, której używa
logowanie hasłem — bez podatności na fixation i bez skutków ubocznych GET-ów.

## Zakres
- `POST /api/auth/magic-link/confirm` (publiczny, CSRF): zużycie tokenu serwisem
  z T-020; sukces → zmiana ID sesji (`HttpServletRequest#changeSessionId`),
  `SecurityContext` jedynego użytkownika w sesji, unieważnienie pozostałych
  aktywnych tokenów (operacja z T-020, polityka D-ML6), odpowiedź `204`;
  porażka → `401` z generycznym `ProblemDetail` identycznym dla wszystkich przyczyn
  (nieistniejący/zużyty/wygasły/unieważniony)
- bramka funkcji (fail-closed): endpoint aktywny tylko gdy funkcja jest
  skonfigurowana wg właściwości z T-020; bez konfiguracji 404
- `Referrer-Policy: same-origin` dla odpowiedzi aplikacji
- żadnych zmian w `/api/auth/login|logout|session|me`

Prerequisity tego taska dostarcza w całości T-020 (operacje serwisu, właściwości
bramki, `Clock`). Task nie zależy od T-021 i nie korzysta z portu mailera — tokeny
w testach wydaje się bezpośrednio serwisem z T-020.

## Poza zakresem
- żądanie linku i wysyłka (T-021); frontend (T-023); rate limiting (T-024);
  usuwanie logowania hasłem (decyzja D-ML5)

## Kryteria akceptacji
- [ ] poprawny token + CSRF → 204, sesja działa na chronionym `/api/**`
      (zweryfikowane na istniejących endpointach, np. `GET /api/auth/me` lub
      `GET /api/jokes` — oba istnieją na `main`)
- [ ] ID sesji przed potwierdzeniem i po nim są różne (anty-fixation)
- [ ] drugi POST tym samym tokenem → 401; dwa równoległe POST-y → dokładnie jedna
      sesja
- [ ] token wygasły/nieistniejący/unieważniony → identyczny generyczny 401 bez
      rozróżnienia przyczyny
- [ ] udane potwierdzenie unieważnia pozostałe aktywne tokeny (zgodnie z domyślną
      polityką D-ML6)
- [ ] żaden GET (w tym `/login/confirm` i `/api/auth/session`) nie zmienia stanu
      tokenu (I7)
- [ ] POST bez poprawnego nagłówka CSRF jest odrzucany i nie zużywa tokenu
- [ ] bez konfiguracji funkcji endpoint zwraca 404

## Testy
- IT (Testcontainers): pełny przepływ wydanie (serwisem z T-020) → potwierdzenie →
  dostęp do `/api/**`; porównanie ciasteczek sesji przed/po; wyścig dwóch
  potwierdzeń; wygaśnięcie (przesunięcie `Clock`); ponowne użycie; unieważnienie
  pozostałych po sukcesie; GET bez skutków; CSRF; 404 bez konfiguracji; sesja
  z magic linku wylogowuje się istniejącym `POST /api/auth/logout`

## Blokada
Bramka zakresu: epik E-ML nie jest częścią `SCOPE.md`, a `SCOPE.md` zmienia wyłącznie
właściciel. Odblokowanie: właściciel akceptuje `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1) i zmienia status tego taska na `todo`. Do tego czasu implementacji
się nie rozpoczyna.
