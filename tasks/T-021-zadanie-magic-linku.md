---
id: T-021
epic: E-ML
title: Żądanie magic linku — endpoint, dozwolony adres, port mailera z mockiem
status: blocked
depends_on: [T-020]
manual_review: true
---

## Cel
Formularz e-mail działa od razu na mocku, bez wyboru dostawcy i bez kosztów, nie
zdradzając, który adres jest dozwolony, i bez możliwości cichego włączenia funkcji
na produkcji bez realnej wysyłki.

## Zakres
- `POST /api/auth/magic-link` (publiczny, CSRF): walidacja formatu, normalizacja
  adresu (trim + małe litery), porównanie z `APP_AUTH_EMAIL`; dla dozwolonego adresu
  wydanie tokenu serwisem z T-020 i wysyłka przez port mailera
- odpowiedź zawsze `202` bez body — identyczna dla adresu dozwolonego, obcego,
  niepowodzenia wysyłki i pełnej puli tokenów (I6)
- port `MagicLinkMailer` + implementacja mock przechwytująca maile w pamięci,
  **dostępna wyłącznie w konfiguracji testowej i deweloperskiej** — w konfiguracji
  produkcyjnej bean mocka nie istnieje; konfiguracja test/dev zgłasza przy tym
  jawnie zdolność `wysylka` do bramki z T-020
- link w mailu: `{APP_PUBLIC_URL}/login/confirm#token=<token>` — token we fragmencie
  URL (nie w query), stała ścieżka, bez parametrów przekierowań
- bramka funkcji: endpoint aktywny wyłącznie przy otwartej bramce `MagicLinkGate`
  z T-020 (komplet zmiennych + zdolności `wysylka` i `limiter`); w przeciwnym razie
  404, a reszta aplikacji działa bez zmian; na produkcji zdolność `wysylka` zgłasza
  dopiero realny adapter (T-025), a `limiter` — T-024, więc do ich merge funkcja
  pozostaje wyłączona — test gwardowy (I8)
- los tokenu po błędzie wysyłki (propozycja w ramach D-ML6, opis
  w `docs/EPIC-MAGIC-LINK.md` §6): **pewna** porażka wysyłki → natychmiastowe
  unieważnienie świeżo wydanego tokenu operacją z T-020 (miejsce w puli zwolnione);
  wynik **niepewny** → token pozostaje aktywny do TTL; w obu przypadkach odpowiedź
  pozostaje `202`; żadnej gwarancji dostarczenia maila
- dev-only podgląd linku do ręcznych testów lokalnych: kanał = endpoint
  deweloperski zwracający ostatni link przechwycony przez mock (dokładny kształt
  potwierdza implementacja), istniejący wyłącznie w konfiguracji deweloperskiej;
  to jedyny — wąski i jawny — wyjątek od zakazu tokenu w odpowiedziach poza mailem
  i obowiązuje wyłącznie w test/dev; w konfiguracji produkcyjnej kanał nie
  istnieje (test gwardowy)
- start aplikacji nadal wymaga hasła jak dotąd — ten task niczego w tym nie zmienia
- `.env.example`: `APP_AUTH_EMAIL`, `APP_PUBLIC_URL`, `APP_MAGIC_LINK_TTL_MINUTES`

## Poza zakresem
- potwierdzanie tokenu i sesja (T-022); realny dostawca (T-025); rate limiting
  (T-024); frontend (T-023)

## Kryteria akceptacji
- [ ] odpowiedź (status i body) jest bajtowo identyczna dla adresu dozwolonego,
      obcego i dla niepowodzenia wysyłki
- [ ] token powstaje wyłącznie dla adresu dozwolonego (obcy adres: zero nowych
      wierszy w bazie)
- [ ] mail z mocka zawiera link `{APP_PUBLIC_URL}/login/confirm#token=...`; poza
      mailem i poza kanałem dev-podglądu (istniejącym wyłącznie w konfiguracji
      test/dev) token i pełny URL nie występują w logach ani odpowiedziach (I5)
- [ ] pewny błąd wysyłki (mock symuluje) nie zmienia odpowiedzi, nie zostawia
      w logu tokenu ani adresata i unieważnia wydany token: po N kolejnych pewnych
      porażkach wysyłki następne żądanie nadal wydaje token (pula niezapełniona
      niedostarczonymi tokenami)
- [ ] przy niepewnym wyniku wysyłki (mock symuluje) token pozostaje aktywny,
      a odpowiedź się nie zmienia
- [ ] przy zamkniętej bramce (brak dowolnej zmiennej lub zdolności) endpoint
      zwraca 404, a aplikacja poza tym działa jak dotąd
- [ ] w konfiguracji produkcyjnej bean mocka mailera i dev-podgląd linku nie
      istnieją (test gwardowy, I8)
- [ ] żądanie bez poprawnego nagłówka CSRF jest odrzucane i nie wydaje tokenu

## Testy
- IT (Testcontainers): wszystkie kryteria powyżej na realnym stosie, w tym asercja
  na brak wiersza dla obcego adresu, przechwycony mail mocka dla dozwolonego,
  404 przy zamkniętej bramce, seria N pewnych porażek wysyłki → kolejne żądanie
  wydaje token, wynik niepewny → token aktywny
- test gwardowy konfiguracji produkcyjnej: brak beana mocka i dev-podglądu,
  endpoint nieaktywny bez realnej wysyłki
- test jednostkowy: normalizacja adresu, budowa linku (fragment, stała ścieżka)

## Blokada
Bramka zakresu: epik E-ML nie jest częścią `SCOPE.md`, a `SCOPE.md` zmienia wyłącznie
właściciel. Odblokowanie: właściciel akceptuje `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1) i zmienia status tego taska na `todo`. Do tego czasu implementacji
się nie rozpoczyna.
