# Epik E-ML: logowanie magic linkiem — projekt

Dokument projektowy epiku E-ML (logowanie jednego użytkownika linkiem z e-maila).
Opisuje przepływ, model danych, politykę tokenów, API, bezpieczeństwo i podział na
taski T-020..T-026.

Stan: **projekt zatwierdzony do formalizacji backlogu; implementacja zablokowana
bramką zakresu.** Epik E-ML nie jest częścią `SCOPE.md` (który zmienia wyłącznie
właściciel). Proponowany dodatek do `SCOPE.md` — sekcja epiku, aktualizacja decyzji D2
i wyjątek kolejności — leży w `docs/MAGIC-LINK-SCOPE-PROPOSAL.md` i czeka na jawną
akceptację właściciela. Do tego czasu wszystkie taski epiku mają `status: blocked`
i żaden agent nie rozpoczyna implementacji. Ten dokument niczego do `SCOPE.md` nie
dodaje — porządkuje decyzje projektowe.

Wszystkie taski E-ML mają `manual_review: true` (dotykają uwierzytelniania).

## 1. Cel

Logowanie jedynego użytkownika bez wpisywania hasła na telefonie: formularz z adresem
e-mail → mail z linkiem → jawne potwierdzenie → ta sama bezpieczna sesja co dziś.
Logowanie hasłem pozostaje nietknięte co najmniej do decyzji D-ML5.

## 2. Stan wyjściowy (zinwentaryzowany 2026-11-27, `main` = 456c426)

- Backlog: na `main` istnieją T-001..T-019; otwarte PR: #10 (T-007), #13 (T-008) —
  żaden nie dodaje tasków `T-020+`. Identyfikatory T-020..T-026 są wolne.
- Uwierzytelnianie (T-006, działa i jest przetestowane): Spring Security, jedno konto,
  `APP_AUTH_USERNAME` + `APP_AUTH_PASSWORD_HASH` (bcrypt), start aplikacji fail-closed
  bez tych zmiennych. `POST /api/auth/login` (204/generyczny 401), `POST /api/auth/logout`,
  publiczny `GET /api/auth/session` (stan sesji + ciasteczko `XSRF-TOKEN`), chroniony
  `GET /api/auth/me` (istnieje na `main` — zweryfikowane w `AuthController`). Sesja
  `JSESSIONID` HttpOnly/SameSite=Lax/Secure wg konfiguracji; CSRF double-submit
  (`CookieCsrfTokenRepository` + nagłówek `X-XSRF-TOKEN`). Całe `/api/**` poza wyjątkami
  wymaga sesji. Fallback SPA w `WebConfig` serwuje `index.html` dla niedopasowanych
  ścieżek — strona `/login/confirm` nie wymaga zmian w serwowaniu statyki.
- Migracje Flyway na `main`: `V1__init.sql`, `V2__joke.sql`. Numerów nie rezerwuje się
  z góry (zasada z `docs/EPIC-1.md`).
- Fakt o wdrożeniu: deploy `main` na Railway crashuje przy starcie z braku
  `APP_AUTH_PASSWORD_HASH` w środowisku usługi. To zamierzone zachowanie fail-closed
  z T-006; naprawa leży po stronie właściciela / finalizacji T-007 (PR #10). **Ten epik
  tego nie naprawia, nie obchodzi i w fazie developmentu od tego nie zależy** — praca
  idzie lokalnie na mocku mailera i Testcontainers. Dopiero T-026 (weryfikacja
  produkcyjna) wymaga zielonego wdrożenia.

## 3. Przepływ

```
[ekran logowania] --POST /api/auth/magic-link {email}--> 202 (zawsze identyczne)
        |                       \ (adres == dozwolony) wydaj token, wyślij mail
        |
[mail] link: {APP_PUBLIC_URL}/login/confirm#token=<token>   (token we FRAGMENCIE URL)
        |
[GET /login/confirm] --> statyczny SPA shell; fragment nie jest wysyłany do serwera,
        |                token NIEzużyty, zero skutków ubocznych
        |  SPA odczytuje token z location.hash, usuwa go z paska adresu (replaceState)
        |  i woła GET /api/auth/session -> ciasteczko XSRF (także na nowym urządzeniu)
        |  strona pokazuje przycisk „Zaloguj się"
        |
[przycisk] --POST /api/auth/magic-link/confirm {token} + X-XSRF-TOKEN-->
        |      atomowe zużycie (dokładnie jeden zwycięzca w wyścigu)
        |      sesja: rotacja ID istniejącej albo jawne utworzenie nowej
        |      (anty-fixation, §9), SecurityContext w sesji
        v
     204 + sesja --> SPA przechodzi do aplikacji jak po zwykłym logowaniu
```

Otwarcie linku na innym urządzeniu niż to, na którym wpisano adres, jest w pełni
wspierane: potwierdzenie dzieje się w przeglądarce, która otworzyła link, i to ona
dostaje sesję. Ciasteczko CSRF załatwia istniejący publiczny `GET /api/auth/session`.

## 4. Model danych

Nowa tabela (migracja `V<n>__login_token.sql`; numer to kolejny wolny dobrany przy
implementacji — taski E1 realizowane równolegle też dokładają migracje):

```sql
create table login_token (
    id         bigint generated always as identity primary key,
    token_hash varchar(64) not null unique,   -- SHA-256 (hex) tokenu, nigdy token jawny
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz,                   -- null = jeszcze nie zużyty
    invalidated_at timestamptz                -- null = nie unieważniony administracyjnie
);
```

Token: 256 bitów z `SecureRandom`, base64url. W bazie wyłącznie SHA-256 — szybki hash
jest tu właściwy, bo wejście ma pełną entropię kryptograficzną (bcrypt/argon2 chronią
hasła niskoentropijne, nie losowe 256-bitowe tokeny); lookup idzie po dokładnym hashu
(indeks unique), bez porównań jawnego sekretu.

Czas wyłącznie przez wstrzykiwany `java.time.Clock` (bean konfiguracyjny) — zarówno
wydawanie/wygasanie tokenów, jak i okna rate limitingu. Testy sterują zegarem
deterministycznie, bez sleepów.

Czyszczenie: wiersze wygasłe/zużyte/unieważnione sprząta proste zapytanie przy okazji
wydawania nowego tokenu (jedno konto — tabela jest mikroskopijna); bez schedulera.

## 5. Inwarianty (każdy pokryty testem w taskach)

- **I1. W bazie nie ma jawnego tokenu** — wyłącznie SHA-256.
- **I2. Token jest użyteczny wtedy i tylko wtedy, gdy** `used_at is null`
  **i** `invalidated_at is null` **i** `now() < expires_at` (zegar z `Clock`).
- **I3. Zużycie jest atomowe, również w wyścigu.** Jedno warunkowe
  `UPDATE ... SET used_at = now() WHERE token_hash = ? AND used_at IS NULL AND
  invalidated_at IS NULL AND expires_at > now()`; liczba zmodyfikowanych wierszy
  1 → zwycięzca, 0 → odmowa. Dwa równoległe POST-y z tym samym tokenem dają dokładnie
  jedną sesję.
- **I4. Anonimowe żądanie nie unieważnia ważnego tokenu (anty denial-of-login).**
  Wydanie nowego tokenu **nie** unieważnia wcześniejszych niezużytych. Zamiast tego
  obowiązuje ograniczona pula aktywnych tokenów (§6). Uzasadnienie zmiany względem
  wcześniejszego draftu: `POST /api/auth/magic-link` jest publiczny, więc polityka
  „nowe żądanie unieważnia poprzednie" pozwalałaby każdemu anonimowo unieważniać
  świeżo wydany link właściciela i w praktyce blokować logowanie magic linkiem
  (denial-of-login). Pozostawienie logowania hasłem nie jest odpowiedzią na ten
  problem — to osobna ścieżka, a blokada ścieżki magic linku pozostawałaby realna.
- **I5. Token, pełny URL linku ani adres z formularza nie trafiają do logów** na
  ścieżce produkcyjnej (logi aplikacji i komunikaty wyjątków). Jedyny wyjątek poza
  treścią maila to wąski kanał dev-podglądu istniejący wyłącznie w konfiguracji
  test/dev (§9).
- **I6. Odpowiedź na żądanie linku jest identyczna** (status i body) niezależnie od
  tego, czy adres jest dozwolony, czy mail wyszedł, czy zadziałał limit per adres,
  czy pula aktywnych tokenów jest pełna.
- **I7. GET niczego nie zmienia.** Żaden GET (strona `/login/confirm`, `/api/auth/...`)
  nie zużywa ani nie unieważnia tokenu.
- **I8. Mock mailera i podgląd linku nie istnieją w konfiguracji produkcyjnej.**
  Pokryte testem gwardowym (§8).

## 6. Polityka puli tokenów (propozycja do decyzji D-ML6)

Spójna polityka „bounded multi-active + consume", zastępująca draftowe
unieważnianie-przy-wydaniu:

- **Pula.** Maksymalnie **N aktywnych** (niezużytych, nieunieważnionych, niewygasłych)
  tokenów naraz; propozycja domyślna N=3, w konfiguracji, odwracalna.
- **Przy pełnej puli** nowe żądanie **nie wydaje tokenu i nie wysyła maila**, a
  odpowiedź pozostaje `202` (I6 — zachowanie obserwowalne identyczne jak limit per
  adres). Zapełnienie puli nie unieważnia żadnego wydanego tokenu, a wszystkie maile
  idą wyłącznie na jedyny dozwolony adres — linki, **które zostały dostarczone**,
  są więc w skrzynce właściciela. Dostawa nie jest jednak gwarantowana (§9, T-025);
  los tokenu po nieudanej wysyłce opisuje punkt „Błąd wysyłki a pula" poniżej.
- **Błąd wysyłki a pula** (propozycja, część D-ML6). Przy **pewnym** błędzie wysyłki
  (synchroniczna, jednoznaczna odmowa — mail na pewno nie wyszedł) świeżo wydany
  token jest natychmiast unieważniany i zwalnia miejsce w puli — seria porażek
  wysyłki nie może zapełnić puli niedostarczonymi tokenami (test w T-021: po N
  pewnych porażkach kolejne żądanie nadal wydaje token). Przy **niepewnym** wyniku
  (timeout, wynik nieznany — mail mógł wyjść) token pozostaje aktywny do TTL,
  bo unieważnienie ryzykowałoby zabicie dostarczonego linku. Tradeoff opisany
  uczciwie: niepewne wyniki mogą czasowo zajmować pulę aż do TTL; krótki TTL to
  ogranicza. Plan nie składa żadnej gwarancji dostarczenia maila.
- **Wyścig wydawania.** Dwa równoległe żądania przy prawie pełnej puli nie mogą
  przekroczyć N. Egzekwowanie transakcyjne po stronie bazy (konkretny mechanizm —
  np. blokada doradcza lub blokujący odczyt puli w transakcji — do wyboru przy
  implementacji); kryterium akceptacji z testem wyścigu jest częścią T-020.
- **Zużycie.** Dokładnie jeden token na jedno potwierdzenie (I3). Propozycja
  domyślna: udane potwierdzenie **unieważnia pozostałe aktywne tokeny** (higiena —
  stare maile przestają logować; wymaga posiadania ważnego tokenu, więc nie da się
  tego wywołać anonimowo i nie tworzy denial-of-login).
- **Zwalnianie puli.** Miejsce w puli zwalnia wygaśnięcie (TTL), zużycie albo
  unieważnienie po udanym logowaniu.

Pozostałe ryzyko opisane uczciwie: przy pełnej puli i oknie limitera napastnik
spamujący formularz sprawia, że *nowe* maile chwilowo nie wychodzą; właściciel używa
dowolnego z już dostarczonych linków albo czeka do końca okna, a logowanie hasłem
działa niezależnie. Gwarancje puli są celowo wąskie i tylko takie: żadna anonimowa
akcja nie unieważnia wydanego ważnego tokenu, a logowanie hasłem pozostaje niezależne
od limitów magic linku. Ryzyka rezydualne pozostają: limit confirm per IP może przy
wspólnym IP czasowo uniemożliwiać potwierdzenie także właścicielowi, a ponawiany atak
może ten stan podtrzymywać przez kolejne okna (tradeoff opisany w §9 i T-024); pula
jest ograniczona i nie gwarantuje dostarczenia żadnego maila (punkt „Błąd wysyłki
a pula" powyżej).

Parametry (N, zachowanie przy pełnej puli, unieważnianie po sukcesie) są propozycjami
domyślnymi do zatwierdzenia lub zmiany przez właściciela (D-ML6); wszystkie odwracalne
jedną zmianą konfiguracji/stałej.

## 7. Kontrakty API i konfiguracja

Zgodnie z `CONVENTIONS.md`: `/api`, JSON, DTO-rekordy, Bean Validation, `ProblemDetail`.

| Metoda i ścieżka | Auth | Żądanie | Odpowiedzi | Task |
| --- | --- | --- | --- | --- |
| `POST /api/auth/magic-link` | publiczny, CSRF | `{"email": "..."}` (walidacja formatu) | zawsze `202` bez body (I6); `400` tylko dla składniowo niepoprawnego żądania; `429` per IP (T-024) | T-021, T-024 |
| `POST /api/auth/magic-link/confirm` | publiczny, CSRF | `{"token": "..."}` | `204` + sesja; `401` generyczny `ProblemDetail` identyczny dla wszystkich przyczyn odmowy (nieistniejący/zużyty/wygasły/unieważniony); `429` per IP (T-024) | T-022, T-024 |
| `GET /login/confirm` | publiczny | — | statyczny SPA shell (istniejący fallback `WebConfig`), zero skutków ubocznych; token podróżuje we fragmencie i nie dociera do serwera | T-023 |

Istniejące endpointy (`/api/auth/login`, `/logout`, `/session`, `/me`) — **bez zmian**.

Nowe zmienne środowiskowe (nazwy do `.env.example`, bez wartości):

| Zmienna | Opis |
| --- | --- |
| `APP_AUTH_EMAIL` | Jedyny dozwolony adres e-mail (porównanie po normalizacji: trim + małe litery). Brak = funkcja wyłączona (fail-closed, §8). |
| `APP_PUBLIC_URL` | Baza absolutnego linku w mailu. Link zawsze wskazuje stałą ścieżkę `/login/confirm` — żadnych parametrów przekierowań. |
| `APP_MAGIC_LINK_TTL_MINUTES` | Opcjonalny TTL; default w kodzie 15 minut. **15 to propozycja odwracalna** (decyzja D-ML4), zmienna jedną wartością konfiguracji. |
| `APP_MAIL_...` | Konfiguracja realnego dostawcy — dopiero w T-025, po D-ML3; nazwy ustali ten task. |

## 8. Bramka funkcji i produkcja (fail-closed)

Zasada nadrzędna: **produkcja nigdy nie wystawia aktywnego logowania magic linkiem
opartego o mock ani bez limitera.** Mechanizm:

- **Jedna wspólna bramka (`MagicLinkGate`), zdefiniowana w T-020, domyślnie
  zamknięta.** Oba endpointy (żądanie i potwierdzenie) pytają wyłącznie tę bramkę.
  Bramka jest otwarta tylko przy komplecie: `APP_AUTH_EMAIL`, `APP_PUBLIC_URL`
  **oraz zgłoszonych zdolnościach (capabilities) `wysylka` i `limiter`**. Brak
  dowolnego elementu → oba endpointy nieaktywne (404), reszta aplikacji działa
  bez zmian.
- **Zdolności zgłaszają późniejsze taski; T-022 nie zależy od portu mailera.**
  T-020 definiuje bramkę i nazwy zdolności jako abstrakcyjne kontrybucje
  konfiguracji — bez odwołania do typu portu, który powstaje dopiero w T-021.
  W konfiguracji produkcyjnej zdolność `wysylka` zgłasza wyłącznie realny adapter
  (T-025), a `limiter` — rate limiting (T-024). W konfiguracji testowej
  i deweloperskiej zdolności zgłasza się **jawnie** — dzięki temu T-021 i T-022
  implementuje się i testuje niezależnie i równolegle po samym T-020.
- **Konsekwencje.** Do merge T-024 i T-025 bramka w produkcji pozostaje zamknięta
  nawet przy ustawionych zmiennych — aktywne publiczne endpointy nie istnieją
  przed limiterem i realnym adapterem. Usunięcie konfiguracji dostawcy (rollback)
  zamyka po restarcie oba endpointy, w tym confirm. Testy wymagane: w T-022 —
  konfiguracja produkcyjna + adres/URL + **istniejący ważny token w bazie** +
  brak adaptera → confirm 404, token nietknięty; w T-021 — test gwardowy:
  konfiguracja produkcyjna bez realnego adaptera → endpoint żądania nieaktywny,
  beany mocka i dev-podglądu nie istnieją (I8).
- Start aplikacji nadal wymaga hasła (`APP_AUTH_PASSWORD_HASH`) jak dotąd — epik tego
  nie zmienia do decyzji D-ML5.

## 9. Bezpieczeństwo — decyzje i uczciwe tradeoffy

Żaden z poniższych środków nie daje absolutnej gwarancji; opis mówi, co który redukuje.

- **Token we fragmencie URL (`#token=...`), nie w query.** Fragment nie jest wysyłany
  przez przeglądarkę w żądaniu HTTP, więc token nie pojawia się w logach dostępowych
  serwera ani pośredników HTTP po drodze i nie wchodzi do nagłówka `Referer`. Skaner
  pocztowy, który tylko wykonuje GET na linku (bez wykonywania JS), nie przekazuje
  tokenu nigdzie. Pozostałości, których fragment nie usuwa: token jest w treści maila
  (dostawca poczty i skrzynka go widzą — to nieusuwalne dla magic linku), trafia do
  historii przeglądarki do czasu `history.replaceState` wykonanego przy załadowaniu
  strony, a skaner wykonujący JS może go odczytać. Koszt: SPA czyta `location.hash`
  zamiast query (porównywalna złożoność). Dodatkowo `Referrer-Policy: same-origin`
  dla odpowiedzi aplikacji jako pas i szelki.
- **Skanery pocztowe / prefetch.** GET nie zużywa tokenu (I7), a token we fragmencie
  nie jest nawet transmitowany przy prefetchu. Zużycie wymaga POST z CSRF i jawnej
  interakcji; skaner wykonujący JS i klikający przyciski mógłby mimo to zużyć token —
  efekt jest bezpieczny-acz-niewygodny (link nie działa, użytkownik prosi o nowy).
- **Brak open redirect.** Stała ścieżka `/login/confirm`, żadnych parametrów
  `redirect`/`next`.
- **Sesja: utworzenie vs rotacja (anty-fixation, nowa przeglądarka).** Dwa jawnie
  rozdzielone przypadki: przy **istniejącej** `HttpSession` (np. anonimowej) —
  rotacja ID (`HttpServletRequest#changeSessionId`) przed zapisaniem
  `SecurityContext`; przy **braku** sesji (typowe dla nowej przeglądarki — publiczny
  `GET /api/auth/session` dostarcza ciasteczko CSRF, ale **nie tworzy**
  `HttpSession`, a samo `changeSessionId` sesji nie utworzy) — jawne utworzenie
  nowej sesji, co samo w sobie jest odporne na fixation (nie istnieje wcześniejszy
  ID do przejęcia). Sekwencja potwierdzenia jest zaprojektowana tak, by zużycie
  tokenu nie mogło zakończyć się wyjątkiem braku sesji już po zużyciu. Oba
  przypadki, wraz z poprawnym utrwaleniem `SecurityContext` strategią sesyjną
  Spring Security, są kryteriami z testami w T-022.
- **CSRF.** Oba POST-y w istniejącym mechanizmie double-submit; brak poprawnego
  `X-XSRF-TOKEN` → 403, token niezużyty.
- **Enumeracja adresu.** Identyczna odpowiedź `202` (I6). Uczciwie: kanał czasowy
  (wysyłka trwa) może teoretycznie różnicować odpowiedzi; nie obiecujemy pełnej
  odporności na timing. Dozwolony adres jest jeden i jest adresem właściciela — to
  sekret o niskiej wartości.
- **Denial-of-login.** Ograniczony, nie wyeliminowany. Polityka puli (§6) daje
  wyłącznie dwie gwarancje: anonimowe żądania nie unieważniają wydanych tokenów
  (limity wygasają z oknem), a logowanie hasłem działa niezależnie od limitów magic
  linku. Ryzyko rezydualne: przy wspólnym IP limit confirm może czasowo blokować
  potwierdzanie także właścicielowi, podtrzymywalnie przez ponawiany atak (punkt
  o rate limitingu niżej), a pula jest ograniczona bez gwarancji dostarczenia maila.
- **Mail = pełny dostęp.** Magic link sprowadza bezpieczeństwo konta do bezpieczeństwa
  skrzynki właściciela. Świadoma zamiana (telefon, wygoda); hasło zostaje jako druga
  ścieżka do decyzji D-ML5. Magic link nie jest „bezpieczniejszy od hasła" — jest
  inaczej-bezpieczny.
- **Dostawa maila.** Niepowodzenie wysyłki nie zmienia odpowiedzi HTTP (I6); błąd
  logowany bez tokenu/URL/adresata (I5). Co najwyżej jedna ponowna próba w adapterze
  (T-025); brak kolejki i gwarancji dostarczenia — użytkownik ponawia z formularza.
  Los tokenu po pewnej porażce (unieważnienie) i po wyniku niepewnym (zostaje do
  TTL) opisuje §6; zachowanie identyczne w mocku i adapterze realnym.
- **Dev-podgląd linku a I5.** Kanał podglądu (propozycja; dokładny kształt
  potwierdza implementacja T-021): endpoint deweloperski zwracający ostatni link
  przechwycony przez mock mailera, istniejący wyłącznie w konfiguracji
  deweloperskiej. Wyjątek od I5 jest wąski i jawny: token i pełny URL mogą
  wystąpić wyłącznie w treści maila oraz w tym kanale, i wyłącznie w konfiguracji
  test/dev. W konfiguracji produkcyjnej kanał nie istnieje — test gwardowy (I8);
  zakaz tokenów w logach produkcyjnych obowiązuje bezwzględnie.
- **Rate limiting (T-024, progi do D-ML4).** Żądania linku: per IP (propozycja
  5/15 min, jawne 429) i per podany adres po normalizacji (propozycja 3/15 min,
  **cichy drop z odpowiedzią 202** — jawny 429 per adres byłby oracle enumeracji).
  Potwierdzenia: per IP (propozycja 10/15 min, jawne 429). Własności anty-lockout —
  ograniczone do tego, co kontrakt rzeczywiście daje: przekroczenie limitów **nie
  unieważnia wydanych tokenów** i **nie blokuje logowania hasłem**, a limity wygasają
  z upływem okna (brak trwałej blokady). Uczciwy tradeoff wspólnego IP: po
  wyczerpaniu limitu confirm per IP również właściciel dzielący IP z napastnikiem
  dostaje 429 do końca okna — token pozostaje wtedy niezużyty i ważny (w granicach
  TTL), a logowanie hasłem działa; ponawiany atak może ten stan podtrzymywać przez
  kolejne okna. Plan **nie** obiecuje nieblokowalności każdej ścieżki logowania.
  Liczniki w pamięci procesu (jedna instancja produkcyjna, SCOPE §3); restart zeruje
  liczniki — akceptowalny tradeoff dla narzędzia jednej osoby. Okna liczone
  z wstrzykiwanego `Clock`.

Do późniejszej weryfikacji (świadomie niezweryfikowane teraz): aktualne zalecenia
branżowe dla magic linków (TTL, obsługa skanerów); cechy konkretnych dostawców e-mail —
**nie przypisujemy żadnemu dostawcy żadnych właściwości**; wymagania DNS dla domeny
nadawcy (o ile właściciel jej zechce — D-ML2).

## 10. Taski i kolejność

Wszystkie taski: `manual_review: true`; do czasu przyjęcia zakresu (D-ML1) wszystkie
mają `status: blocked` z bramką zakresu w sekcji „Blokada".

| Task | Treść | depends_on | Dodatkowe blokady poza bramką zakresu |
| --- | --- | --- | --- |
| T-020 | tokeny: model, bramka `MagicLinkGate`, konfiguracja+Clock, pula, atomowe zużycie | T-006 | — |
| T-021 | żądanie linku: endpoint, dozwolony adres, port mailera, mock (test/dev) | T-020 | — |
| T-022 | potwierdzenie: zużycie → sesja (utworzenie/rotacja), anty-fixation | T-020 | — |
| T-023 | frontend: formularz e-mail i strona potwierdzenia (fragment) | T-021, T-022 | — |
| T-024 | rate limiting + zdolność `limiter` dla bramki | T-021, T-022 | — |
| T-025 | adapter realnego dostawcy | T-021, T-024 | decyzje D-ML2, D-ML3 |
| T-026 | włączenie na produkcji, decyzja o haśle | T-023, T-024, T-025 | decyzja D-ML5 + działające wdrożenie |

```
T-006 (done) ─▶ T-020 ─┬─▶ T-021 ─┬─▶ T-023 ──────────┐
                       │          ├─▶ T-024 ─┐        ├─▶ T-026
                       └─▶ T-022 ─┘          ├─▶ T-025 ┘
                                  (T-021) ───┘
```

- T-021 i T-022 idą **równolegle** po T-020: różne endpointy, wspólny serwis
  i kompletna bramka `MagicLinkGate` zdefiniowane w T-020. T-022 nie zależy od
  T-021 i nie korzysta z portu mailera — jego prerequisity (operacje serwisu,
  bramka ze zdolnościami zgłaszanymi jawnie w konfiguracji testowej, `Clock`)
  dostarcza w całości T-020 (§8).
- Ścieżka bez żadnej decyzji zewnętrznej: T-020 → (T-021 ∥ T-022) → (T-023 ∥ T-024) —
  całość działa lokalnie na mocku, zero kosztów, zero sekretów.
- T-025 dopiero po wyborze dostawcy (D-ML2/D-ML3); `depends_on` zawiera T-024, żeby
  produkcyjne włączenie nie było możliwe przed limiterem (§8).
- T-026 na końcu: wymaga zielonego realnego wdrożenia i decyzji właściciela.
- Epik nie koliduje z trwającym E1 (T-015..T-019): inne pakiety (`auth` vs `joke`),
  migracje biorą kolejne wolne numery w kolejności merge'ów, konflikty co najwyżej
  trywialne (`.env.example`, `client.ts`).

## 11. Migracje, wdrożenie, rollback

**Migracje.** Jedna addytywna migracja (tabela `login_token`) w T-020; numer dobrany
przy implementacji. Żadna migracja epiku nie modyfikuje ani nie usuwa istniejących
danych i tabel.

**Wdrożenie.** Merge tasków T-020..T-024 jest bezpieczny nawet przy crashującym
deployu (zmiany addytywne, funkcja nieaktywna bez pełnej konfiguracji — §8).
Sekwencja produkcyjna: (1) właściciel doprowadza deploy do zieleni (niezależnie od
epiku, por. §2), (2) merge tasków wdraża kod z funkcją wyłączoną, (3) po merge T-025
właściciel ustawia `APP_AUTH_EMAIL`, `APP_PUBLIC_URL` i zmienne dostawcy → funkcja
się włącza, (4) weryfikacja w T-026.

**Rollback.** Pierwszy wybór: usunięcie `APP_AUTH_EMAIL` (lub zmiennych dostawcy) na
Railway i restart — endpointy magic linku gasną, logowanie hasłem działa dalej, **o ile
`APP_AUTH_PASSWORD_HASH` pozostaje skonfigurowane**. Uczciwe zastrzeżenie: gdyby
decyzja D-ML5 kiedyś usunęła hasło, nie istnieje automatyczny powrót do logowania
hasłem — wymagałby ponownego skonfigurowania hasha przez właściciela; dlatego ten plan
nie obiecuje rollbacku „bez hasła → hasło" i do decyzji D-ML5 hasła nie rusza.
Revert kodu to ostateczność: migracja zostaje (CONVENTIONS zabrania usuwania migracji),
a `ddl-auto=validate` toleruje osieroconą tabelę tylko przy spójnym cofnięciu encji —
stąd preferencja dla rollbacku zmiennymi środowiskowymi. Tokeny w tabeli to wyłącznie
hashe o krótkim TTL — brak danych wymagających czyszczenia.

## 12. Decyzje właściciela (wszystkie otwarte)

| ID | Decyzja | Domyślna propozycja |
| --- | --- | --- |
| D-ML1 | Przyjęcie zakresu E-ML: akceptacja `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`, aktualizacja `SCOPE.md` przez właściciela, odblokowanie tasków (`blocked` → `todo` dla T-020..T-024) | przyjąć jak w propozycji |
| D-ML2 | Dozwolony adres e-mail (`APP_AUTH_EMAIL`) oraz nadawca/domena wysyłki | brak — wyłącznie właściciel; bez zakładania domen i kont w ramach epiku |
| D-ML3 | Dostawca e-mail i miesięczny limit kosztu | brak rekomendacji w tej fazie (bez weryfikacji online nie przypisujemy dostawcom właściwości); do decyzji praca na mocku |
| D-ML4 | TTL tokenu i progi rate limitingu | TTL 15 min; 5/15 min per IP, 3/15 min per adres, 10/15 min confirm per IP — wszystko defaulty odwracalne jedną zmianą konfiguracji |
| D-ML5 | Los logowania hasłem po działającym magic linku | zostawić hasło jako ścieżkę awaryjną; nie usuwać fail-closed |
| D-ML6 | Polityka puli tokenów: rozmiar N, zachowanie przy pełnej puli, unieważnianie pozostałych po udanym logowaniu, los tokenu po pewnym/niepewnym błędzie wysyłki | N=3; pełna pula → 202 bez wydania i bez maila; po sukcesie unieważnić pozostałe; pewna porażka wysyłki → unieważnienie tokenu, wynik niepewny → token zostaje do TTL (§6) |

## 13. Zmiany względem draftu planisty i dlaczego

Draft (scratch, poza repo) został przeniesiony do tego dokumentu z następującymi
zmianami merytorycznymi:

1. **Polityka tokenów (dawne I4).** Draft: „nowe żądanie unieważnia poprzednie
   niezużyte tokeny". Zmienione na ograniczoną pulę aktywnych tokenów (§6), bo
   publiczny endpoint żądania pozwalałby każdemu anonimowo unieważnić świeży link
   właściciela — wektor denial-of-login. Draft zbywał to uwagą, że hasło zostaje;
   to nie jest brak lockoutu, tylko lockout jednej ze ścieżek. Nowa polityka dodaje
   decyzję D-ML6 i jawny wymóg race-safe wydawania (test w T-020).
2. **Token we fragmencie URL zamiast query.** Draft wybierał query + `replaceState`.
   Zmienione na fragment (`#token=...`): nie jest wysyłany do serwera, więc znika
   cała klasa logów serwera/pośredników i prefetch bez JS nie transmituje tokenu
   nigdzie. Tradeoff i pozostałości opisane w §9 bez gwarancji absolutnych.
3. **Fail-closed względem mailera na produkcji.** Draft bramkował funkcję tylko na
   `APP_AUTH_EMAIL`/`APP_PUBLIC_URL`, więc po T-021 produkcja z ustawionymi zmiennymi
   wystawiałaby aktywne logowanie oparte o mock (mail nigdzie nie dochodzi, a dev-podgląd
   linku byłby jedyną drogą — niedopuszczalne). Teraz warunkiem jest także realna
   implementacja wysyłki, mock i podgląd istnieją wyłącznie w test/dev, z testem
   gwardowym (I8, §8).
4. **Endpointy publiczne a limiter.** Dodano `depends_on: T-024` do T-025, przez co
   produkcyjne włączenie funkcji (wymagające realnego adaptera) jest niemożliwe przed
   rate limitingiem. W fazie przed T-025 endpointy są na produkcji martwe (pkt 3).
5. **T-022: własne prerequisity i istniejące endpointy.** Draft opisywał bramkę T-022
   jako „fail-closed jak w T-021" (ukryta zależność) i używał w kryterium
   `/api/auth/me`. Teraz bramka i właściwości konfiguracji są zdefiniowane w T-020,
   a T-022 żadnej treści T-021 nie potrzebuje. Endpoint `/api/auth/me` **istnieje na
   `main`** (zweryfikowane w `pl.bankzartow.auth.AuthController` — chroniony, używany
   przez testy T-006 jako reprezentant `/api/**`); kryteria T-022 wskazują zweryfikowane
   istniejące endpointy (`/api/auth/me`, `/api/jokes`).
6. **Deterministyczny zegar.** `java.time.Clock` wstrzykiwany od T-020 i użyty w
   T-024 — TTL i okna limitów testowane bez sleepów; TTL 15 min pozostaje odwracalną
   propozycją (D-ML4).
7. **Bramka zakresu zamiast gotowego backlogu.** Wszystkie taski rodzą się ze
   `status: blocked` i bramką „zakres E-ML nieprzyjęty" — `SCOPE.md` zmienia wyłącznie
   właściciel, a propozycja zmiany leży w `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`. Draft
   zakładał `todo` od urodzenia dla T-020..T-024.
8. **Rollback bez obietnic.** Dopisano wprost, że powrót do logowania hasłem istnieje
   tylko przy skonfigurowanym `APP_AUTH_PASSWORD_HASH`; plan nie obiecuje rollbacku
   do hasła po jego ewentualnym usunięciu (D-ML5).
9. **Schemat tabeli.** Dodano kolumnę `invalidated_at` (rozdział unieważnienia od
   zużycia), potrzebną dla polityki puli i uczciwej telemetrii stanu tokenu.

Bez zmian pozostały m.in.: przepływ GET-nie-zużywa/POST-potwierdza, atomowe zużycie
(I3), anty-fixation, CSRF, identyczna odpowiedź 202 (I6), port mailera z mockiem,
brak wyboru dostawcy i brak kosztów w fazie developmentu, relacja do crasha Railway.

### Zmiany po pierwszej rundzie review (PR #17)

Niezależny reviewer zgłosił 5 ustaleń (2×P1, 3×P2); wszystkie uwzględnione:

1. **(P1) Kompletna wspólna bramka z T-020.** Bramka `MagicLinkGate` przeniesiona
   w całości do T-020: domyślnie zamknięta, otwierana kompletem zmiennych **i**
   zdolności `wysylka`+`limiter` zgłaszanych jako abstrakcyjne kontrybucje
   konfiguracji (prod: tylko realny adapter i limiter; test/dev: jawnie). Usuwa to
   ukrytą zależność T-022 od portu z T-021 i lukę „confirm aktywny przed
   T-024/T-025 albo po usunięciu konfiguracji dostawcy". Nowy wymagany test
   w T-022: produkcja + adres/URL + istniejący ważny token + brak adaptera →
   confirm 404, token nietknięty (§8).
2. **(P1) Sesja w nowej przeglądarce.** `GET /api/auth/session` dostarcza ciasteczko
   CSRF, ale nie tworzy `HttpSession`, a samo `changeSessionId` sesji nie utworzy —
   literalna realizacja draftu mogła zakończyć potwierdzenie wyjątkiem po zużyciu
   tokenu. Rozdzielono jawne utworzenie nowej sesji (brak `JSESSIONID`) od rotacji
   istniejącej (sesja anonimowa); oba przypadki plus utrwalenie `SecurityContext`
   właściwą strategią sesyjną są kryteriami z testami w T-022 (§9).
3. **(P2) Ograniczony claim anty-lockout.** Cel i kryteria T-024 nie obiecują już
   nieblokowalności „żadnej ścieżki": gwarancje zawężone do ważności wydanych
   tokenów i niezależności logowania hasłem; tradeoff wspólnego IP (confirm 429
   także dla właściciela do końca okna, podtrzymywalny przez ponawiany atak)
   opisany jawnie; testy limitów żądań i potwierdzeń rozdzielone, z kryterium
   „token niezużyty przy 429" (§9, T-024).
4. **(P2) Pula a brak gwarancji dostawy.** Usunięto fałszywe uzasadnienie
   „właściciel zawsze ma komplet linków w skrzynce". Dodano politykę losu tokenu:
   pewna porażka wysyłki → natychmiastowe unieważnienie (seria porażek nie
   zapełnia puli; test w T-021), wynik niepewny → token zostaje do TTL; tradeoff
   opisany, wybór parametrów pozostaje przy właścicielu w D-ML6 (§6).
5. **(P2) Kanał dev-podglądu jako jawny wyjątek.** Sprzeczność „zakres wymaga
   podglądu, KA zakazuje tokenu w odpowiedziach" usunięta: kanał sprecyzowany
   (dev-only endpoint z ostatnim linkiem z mocka), wyjątek od I5 wąski i jawny
   (tylko mail + ten kanał, tylko test/dev), produkcja bez kanału z testem
   gwardowym (§9, I5, T-021).

### Zmiany po drugiej rundzie review (PR #17)

Jedno pozostałe ustalenie (P2): dwa sformułowania wciąż deklarowały za dużo —
„nie blokuje trwale żadnej ścieżki logowania" (§6) i „Denial-of-login. Rozwiązany
polityką puli" (§9) — sprzecznie z opisanym w tym samym dokumencie podtrzymywalnym
ograniczeniem confirm przy wspólnym IP. Oba zawężone do dwóch faktycznych gwarancji
(anonimowe żądania nie unieważniają wydanych tokenów; logowanie hasłem niezależne)
z jawnym ryzykiem rezydualnym: czasowa, podtrzymywalna blokada confirm przy wspólnym
IP oraz ograniczona pula bez gwarancji dostarczenia maila. Przy przeglądzie całości
pod kątem równoważnych nadmiernych gwarancji dodano też „trwałego" w tytule T-024
(„bez trwałego lockoutu właściciela"). Polityka, kryteria akceptacji i decyzje
D-ML1..D-ML6 bez zmian.
