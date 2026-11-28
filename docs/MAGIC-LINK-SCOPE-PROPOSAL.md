# Propozycja zmiany SCOPE.md dla epiku E-ML (do decyzji właściciela)

Status: **propozycja — czeka na jawną akceptację właściciela (decyzja D-ML1).**

`SCOPE.md` i kryteria akceptacji zmienia wyłącznie właściciel (`AGENTS.md`,
`SCOPE.md` §8). Ten dokument niczego w `SCOPE.md` nie zmienia — zawiera gotowy,
minimalny tekst dodatku, który właściciel może przyjąć, zmienić albo odrzucić.

**Bramka implementacji:** dopóki właściciel nie przyjmie tej propozycji (wprowadzając
zmiany do `SCOPE.md` lub jawnie je akceptując), wszystkie taski epiku E-ML
(T-020..T-026) pozostają `status: blocked` i żaden agent nie rozpoczyna implementacji.
Samo istnienie plików tasków i dokumentu `docs/EPIC-MAGIC-LINK.md` nie autoryzuje
żadnej pracy nad kodem.

Po akceptacji właściciel:
1. wprowadza do `SCOPE.md` punkty A–C poniżej (dosłownie lub po swojemu),
2. zmienia `status: blocked` → `status: todo` w T-020..T-024 (T-025 i T-026 pozostają
   `blocked` do decyzji D-ML2/D-ML3 oraz D-ML5 + działającego wdrożenia),
3. opcjonalnie rozstrzyga od razu D-ML4 i D-ML6 (defaulty opisane w
   `docs/EPIC-MAGIC-LINK.md` §12 są odwracalne i nie blokują startu T-020).

---

## A. Nowa sekcja epiku w `SCOPE.md` §4 (propozycja tekstu)

### Epik E-ML: logowanie magic linkiem (rozszerzenie decyzji D2)

Logowanie jedynego użytkownika bez hasła na telefonie: formularz z adresem e-mail,
mail z jednorazowym linkiem, jawne potwierdzenie i ta sama sesja co przy logowaniu
hasłem. Projekt epiku opisuje `docs/EPIC-MAGIC-LINK.md`; realizują go taski
T-020..T-026 (wszystkie `manual_review: true`).

Zakres:
- nadal jedno konto i jeden skonfigurowany adres e-mail (zmienna środowiskowa), bez
  rejestracji i bez wielu użytkowników
- jednorazowy, wygasający token przechowywany wyłącznie jako hash; otwarcie linku
  (GET) nie zużywa tokenu, zużywa go dopiero jawne potwierdzenie (POST), atomowo
  także w wyścigu
- ograniczona pula aktywnych tokenów; żądania anonimowe nie unieważniają wydanego
  ważnego linku (ochrona przed denial-of-login)
- po potwierdzeniu istniejący mechanizm sesji i CSRF, z ochroną przed session fixation
- rate limiting żądań i potwierdzeń bez trwałej blokady właściciela
- port wysyłki maila z implementacją mock dla testów i pracy lokalnej; realny
  dostawca dopiero po osobnej decyzji właściciela
- fail-closed: wspólna, domyślnie zamknięta bramka obu endpointów; bez pełnej
  gotowości (adres, publiczny URL, a w produkcji realna wysyłka i limiter)
  funkcja — łącznie z potwierdzaniem — jest wyłączona, a aplikacja działa jak
  dotąd; mock wysyłki nigdy nie działa w konfiguracji produkcyjnej

Poza zakresem:
- rejestracja, wielu użytkowników, role (§5 bez zmian)
- wybór i konfiguracja dostawcy e-mail, zakładanie kont/domen, koszty — jawne decyzje
  właściciela
- usunięcie logowania hasłem lub osłabienie fail-closed startu aplikacji — osobna
  decyzja właściciela podejmowana dopiero po działającym magic linku na produkcji
- naprawa obecnego stanu wdrożenia Railway (niezależna od epiku)
- trwałe sesje w bazie, „zapamiętaj mnie", SMS, TOTP

Kryteria akceptacji:
- logowanie magic linkiem tworzy tę samą sesję co logowanie hasłem; token jest
  jednorazowy, ograniczony czasowo, a w bazie istnieje wyłącznie jego hash
- otwarcie linku bez potwierdzenia nie zużywa tokenu; dwa równoległe potwierdzenia
  tego samego tokenu dają dokładnie jedną sesję
- odpowiedź na żądanie linku nie zdradza, czy podany adres jest dozwolony
- anonimowe żądania nie unieważniają wydanego ważnego linku ani nie blokują
  logowania hasłem; limity wygasają samoczynnie
- bez pełnej gotowości oba endpointy funkcji są wyłączone (także potwierdzanie,
  również przy istniejącym ważnym tokenie), a mock wysyłki nie istnieje
  w konfiguracji produkcyjnej (potwierdzone testami)
- realny dostawca e-mail podłączany wyłącznie po decyzji właściciela; testy działają
  na mocku, sekrety poza repozytorium

## B. Aktualizacja decyzji D2 w `SCOPE.md` §7 (propozycja tekstu)

Obecnie: „**D2. Logowanie.** Rozstrzygnięte: aplikacja wystawiona na Railway, jedno
konto z hasłem ustawianym w zmiennych środowiskowych."

Proponowane brzmienie:

> **D2. Logowanie.** Rozstrzygnięte z rozszerzeniem: jedno konto z hasłem ustawianym
> w zmiennych środowiskowych (bez zmian) oraz — po wdrożeniu epiku E-ML — dodatkowe
> logowanie magic linkiem na jeden skonfigurowany adres e-mail właściciela, domyślnie
> wyłączone do czasu pełnej konfiguracji produkcyjnej. Los logowania hasłem po
> działającym magic linku to osobna decyzja właściciela (D-ML5 w
> `docs/EPIC-MAGIC-LINK.md`); do jej podjęcia hasło i fail-closed start pozostają
> bez zmian.

## C. Wyjątek kolejności w `SCOPE.md` §6 (propozycja tekstu)

Dopisek na końcu sekcji 6:

> Epik E-ML jest niezależny od epików produktowych (E1–E3, E-RX): dotyczy pakietu
> `auth` i ekranu logowania, nie modelu żartów ani edytora. Może być realizowany
> równolegle z bieżącymi pracami bez zmiany kolejności epików produktowych.
> Kolejność wewnątrz epiku: T-020, następnie T-021 i T-022 równolegle, potem T-023
> i T-024, T-025 po wyborze dostawcy e-mail, T-026 na końcu — po zielonym realnym
> wdrożeniu i decyzjach właściciela. Dopisanie tasków E-ML nie autoryzuje żadnych
> zmian w pozostałych epikach.

---

## Co ta propozycja zmienia, a czego nie

Zmienia (po akceptacji): §4 (nowy epik), §6 (dopisek o równoległości), §7 (D2).

Nie zmienia: §5 „poza zakresem" (nadal brak rejestracji i wielu użytkowników —
magic link tego nie narusza), §3 stacku i założeń, §8 zasad dla agentów, żadnych
istniejących epików, tasków T-001..T-019 ani ich kryteriów.

Decyzje pozostające otwarte niezależnie od akceptacji zakresu: D-ML2 (adres/nadawca),
D-ML3 (dostawca i limit kosztu), D-ML4 (TTL i progi limitów — defaulty odwracalne),
D-ML5 (los hasła), D-ML6 (parametry puli tokenów). Pełna lista z propozycjami:
`docs/EPIC-MAGIC-LINK.md` §12.
