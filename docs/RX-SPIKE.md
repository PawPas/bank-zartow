# RX-SPIKE: reaktywne AI — decyzja go/no-go (T-008)

- **Task:** T-008 (epik E-RX), `manual_review: true`
- **Data sprawdzenia źródeł:** 2026-11-02
- **Charakter:** spike dokumentacyjny. Brak kodu adaptera, brak wywołań realnego dostawcy, brak kluczy, brak logowania, brak wydatków. Weryfikacją jest przegląd tego dokumentu przez właściciela.
- **Zasada rozstrzygania:** fail-closed. Jeśli którykolwiek wymagany punkt jest niepotwierdzony lub zabroniony, wynik całości to `no-go`. Dostępność techniczna nie jest pozwoleniem.

## 1. Co rozstrzyga ten spike

Decyzja D3 w `SCOPE.md` (dostawca AI dla przycisku „zaproponuj 3 inne puenty” / „skróć wprowadzenie” w edytorze E2). Zgodnie ze zleceniem badamy **ścieżkę subskrypcyjną w pierwszej kolejności**, bez automatycznego przechodzenia na płatne API.

Konkretna badana ścieżka (realna trasa produktu), odwzorowana z tasków E-RX:

- dostawca: Anthropic Claude;
- **dostęp: logowanie subskrypcyjne (Claude Pro/Max) przez Claude Code / Claude Agent SDK**, czyli token OAuth konta Claude zamiast klucza API;
- aplikacja: własna usługa Spring Boot (bank żartów), która woła model z backendu, konsumuje strumień przez `WebClient`/`Flux` (T-010, T-012) i serwuje go do przeglądarki przez SSE;
- hosting: Railway, jedna usługa, tryb **nienadzorowany** (bez człowieka przy każdym wywołaniu);
- liczba użytkowników: **jeden** (właściciel).

Pytanie spike’u ma dwie części, które muszą być spełnione **łącznie**: (A) czy jest to wykonalne technicznie i (B) czy jest to **dozwolone** przez oficjalne warunki dostawcy dla tej konkretnej ścieżki. Odpowiedź na (B) przesądza całość.

## 2. Metoda i ograniczenia

- Wyłącznie odczyt oficjalnej dokumentacji i warunków Anthropic/Claude (fetch/search). Żadnych wywołań API, kluczy, OAuth, zmian konta, wdrożeń ani wyłączania sandboxa.
- Nie opieram się na wcześniejszych twierdzeniach asystentów ani na blogach osób trzecich. Każdy wniosek jest podparty linkiem do źródła pierwotnego i datą sprawdzenia (sekcja 7).
- Ceny podaję wyłącznie z oficjalnego cennika z datą sprawdzenia; tam gdzie liczby nie są potwierdzone, oznaczam je jako nieznane.

## 3. Ustalenia techniczne

Wniosek techniczny: **strumieniowanie jest wykonalne i nadaje się do konsumpcji przez `WebClient`/`Flux`.** To jednak dotyczy **API Claude**, a nie decyduje o legalności ścieżki subskrypcyjnej (sekcja 4).

### 3.1. Strumieniowanie (SSE)

- Messages API pozwala ustawić `"stream": true` i strumieniować odpowiedź przyrostowo przez **server-sent events (SSE)**. Cytat: *„When creating a Message, you can set `\"stream\": true` to incrementally stream the response using server-sent events (SSE).”* (Streaming messages, 2026-11-02).
- Kontrakt zdarzeń (nazwany typ SSE + pole `type` w danych): `message_start` → dla każdego bloku `content_block_start` / wiele `content_block_delta` / `content_block_stop` → `message_delta` → `message_stop`, pomiędzy nimi możliwe `ping`. Cytat: *„Each event uses an SSE event name (for example, `event: message_stop`), and includes the matching event `type` in its data.”*
- To jest zwykły `text/event-stream`, więc po stronie Spring MVC można go konsumować reaktywnie przez `WebClient` jako `Flux<ServerSentEvent<...>>`/`Flux<String>`, co odpowiada architekturze z T-009/T-010 (rdzeń zwraca `Flux`, endpoint mostkuje do SSE, serwer pozostaje w modelu MVC). Istnieje też oficjalny Java SDK (`com.anthropic`) ze strumieniowaniem, ale rdzeń projektu zakłada własny most `WebClient`→`Flux` (T-012), co jest zgodne z kontraktem SSE powyżej.

### 3.2. Zgłaszanie błędów

- Standardowe kody HTTP i typy błędów: `400 invalid_request_error`, `401 authentication_error`, `402 billing_error`, `403 permission_error`, `404 not_found_error`, `409 conflict_error`, `413 request_too_large`, `429 rate_limit_error`, `500 api_error`, `504 timeout_error`, `529 overloaded_error`. Ciało błędu to JSON z `error.type`, `error.message` i `request_id` (Claude API errors, 2026-11-02).
- **Błąd w trakcie strumienia:** po zwróceniu HTTP 200 błąd może przyjść jako zdarzenie SSE, nie jako ciche zamknięcie. Cytat: *„The API might occasionally send errors in the event stream. For example, during periods of high usage, you might receive an `overloaded_error` …”* z przykładem `event: error` / `data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}`. To bezpośrednio wspiera wymóg T-009/T-010, by błąd kończył strumień jawnym zdarzeniem błędu.
- To zgodne z regułą „brak automatycznego fallbacku”: błąd/limit kończy strumień błędem domenowym, a nie podmianą dostawcy.

### 3.3. Anulowanie (z jawnym rozdzieleniem lokalne vs po stronie dostawcy)

- **Potwierdzone (lokalnie):** strumień to długo żyjące połączenie HTTP SSE. W modelu Reactor anulowanie subskrypcji `Flux` zamyka połączenie HTTP i zatrzymuje **odbiór** dalszych zdarzeń po stronie aplikacji; to odwzorowuje lokalną propagację `cancel` z T-009/T-010 (zatrzymanie emisji do góry, zamknięcie SSE).
- **Niepotwierdzone (po stronie dostawcy):** przywołana sekcja „Long requests” (Claude API errors, 2026-11-02) opisuje jedynie timeouty, keep-alive i TCP socket options dla długich żądań. **Żadne sprawdzone źródło nie potwierdza, że po zamknięciu połączenia SSE dostawca przerywa obliczenia (compute) ani przestaje naliczać koszt za już wygenerowane/generowane tokeny.** Lokalne zatrzymanie odbioru nie jest dowodem na zatrzymanie generowania ani rozliczenia u dostawcy.
- **Konsekwencja:** semantykę „anulowanie zatrzymuje generowanie i naliczanie u dostawcy” traktujemy jako **nieznaną** i czynimy z niej punkt weryfikacyjny przed T-012 (sekcja 6). Nie składamy obietnicy zatrzymania compute/billing po stronie dostawcy.

### 3.4. Limity i pojemność (dotyczą API, nie subskrypcji)

- Dwa rodzaje limitów: **spend limits** (miesięczny pułap kosztu) oraz **rate limits** (RPM / input-tokens-per-minute / output-tokens-per-minute), zależne od **usage tier** (Rate limits, 2026-11-02).
- Miesięczne pułapy wydatku wg tieru: **Start $500**, **Build $1 000**, **Scale $200 000**; Custom bez pułapu. Można też ustawić **własny** limit wydatku poniżej pułapu tieru (w Console, strona Billing).
- Po osiągnięciu pułapu żądania zwracają `429 rate_limit_error`; przy pułapie tieru brak nagłówka `retry-after` (ponawianie nie pomaga do końca miesiąca), a przy własnym ustawionym limicie zwracany jest `400 invalid_request_error`.
- Nowe organizacje mogą startować w **Evaluation tier** z limitami niższymi niż standardowe. Nagłówki `anthropic-ratelimit-*` i `retry-after` pozwalają reagować na limity.

## 4. Ustalenia prawne / warunki dostawcy

Wniosek prawny dla badanej ścieżki subskrypcyjnej: **dopuszczalność niepotwierdzona → `no-go` (fail-closed).** To nie jest kategoryczny, ogólny zakaz ani teza „wyłącznie klucz API”. Jeden wariant subskrypcyjny jest przez źródła wprost niedozwolony, inny ma warunkowy wyjątek, którego spełnienia dla tego projektu źródła nie potwierdzają. Poniżej rozdzielam trzy warianty uwierzytelniania/wykonania i cytuję źródła pierwotne.

### 4.1. Trzy różne warianty dostępu subskrypcyjnego

Mówiąc „ścieżka subskrypcyjna” trzeba rozróżnić co najmniej trzy odrębne przepływy, bo źródła traktują je inaczej:

- **(A) Własny/reimplementowany klient wołający Claude na poświadczeniach subskrypcji** (np. most `WebClient`→`Flux` do SSE z tokenem OAuth konta Claude), lub oferowanie logowania Claude.ai we własnej aplikacji — **niedozwolone**. Claude Code „Legal and compliance” (2026-11-02): *„Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, Max, or Ultra plan credentials on behalf of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must complete through Anthropic's own flow.”*
- **(B) Agent SDK z logowaniem subskrypcyjnym w produkcie** — **niedozwolone bez uprzedniej zgody**. Agent SDK overview (2026-11-02): *„Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login or rate limits for their products, including agents built on the Claude Agent SDK. Use the API key authentication methods … instead.”* oraz *„Use of the Claude Agent SDK is governed by Anthropic's Commercial Terms of Service, including when you use it to power products and services that you make available to your own customers and end users.”*
- **(C) Hosting niemodyfikowanego binarnego Claude Code, do którego użytkownik loguje się własną subskrypcją przez natywny przepływ Anthropic** — **dopuszczalne warunkowo**. Claude Code „Legal and compliance” (2026-11-02): *„preinstalling or running Claude Code in your products or services (e.g. in hosted sandboxes or other agent infrastructure) requires agreeing to our Commercial Terms of Service and complying with the conditions below”*, w tym *„The Claude Code binary must not be modified”* oraz *„Customers may not pay for, resell, or intermediate Claude usage on their end users' behalf. Each end user must authenticate with their own Anthropic API key, Claude subscription plan credentials, or 3P inference provider credential … billed directly to the end user.”* Sekcja uwierzytelniania potwierdza wyjątek: *„Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription, including where a platform hosts Claude Code …”.*

### 4.2. Jak badana trasa produktu mapuje się na te warianty

- Architektura z T-010/T-012 (własny most `WebClient`→`Flux`→SSE wołający dostawcę z backendu) to **wariant (A)/(B)**: reimplementowany klient / osadzenie zdolności Claude w autorskim produkcie na poświadczeniach subskrypcji. Dla tego wariantu źródła są jednoznaczne — **niedozwolone** bez uprzedniej zgody Anthropic; zalecają klucz API.
- Wariant **(C)** nie jest wykluczony kategorycznie, ale **nie odpowiada** projektowanej architekturze: bank żartów nie uruchamia niemodyfikowanego binarnego Claude Code z logowaniem użytkownika przez natywny przepływ Anthropic, tylko sam konsumuje strumień dostawcy. Przejście na (C) byłoby inną architekturą, wymagałoby akceptacji Commercial Terms i spełnienia warunków wyjątku, a dodatkowo **nie jest potwierdzone**, czy jednoosobowe, nienadzorowane, osadzone w produkcie użycie spełnia założenie *„ordinary, individual usage”* (patrz 4.3) oraz warunek braku pośredniczenia. Samo „jest hostowane / bez nadzoru / w produkcie” nie przesądza ani za, ani przeciw wyjątkowi — dlatego fail-closed.

### 4.3. Dodatkowe ograniczenia z dokumentacji Claude Code i Consumer Terms

- Claude Code „Legal and compliance” (2026-11-02): OAuth subskrypcyjny *„is intended exclusively for purchasers … and is designed to support ordinary use of Claude Code and other native Anthropic applications”*; *„Advertised usage limits for Pro, Max, and Ultra plans assume ordinary, individual usage of Claude Code and the Agent SDK”*; *„Anthropic reserves the right to take measures to enforce these restrictions …”.* To zawęża subskrypcję do zwykłego, indywidualnego użycia Claude Code / natywnych aplikacji; nie przesądza wprost statusu autorskiej usługi serwerowej, stąd — niepotwierdzone.
- **Consumer Terms of Service (data wejścia: 2025-10-08) — cytaty dokładne i poprawnie umiejscowione:**
  - **§2 (ewaluacja):** *„Use of our Services for evaluation purposes are for your personal, non-commercial use only.”* Ograniczenie „personal, non-commercial” jest tu **związane z ewaluacją**, nie jest ogólną klauzulą użycia.
  - **§3.7 (dostęp automatyczny) — z wyjątkami:** zakazane jest *„Except when you are accessing our Services via an Anthropic API Key or where we otherwise explicitly permit it, to access the Services through automated or non-human means, whether through a bot, script, or otherwise.”* Czyli dostęp automatyczny jest dozwolony **przez klucz API** albo **gdy Anthropic wyraźnie na to zezwoli**; dla trasy subskrypcyjnej żaden z tych wyjątków nie jest potwierdzony.
  - W Consumer Terms występuje też, w sekcji ograniczenia odpowiedzialności/wyłączenia gwarancji, zdanie o niekorzystaniu z Usług w celach komercyjnych/biznesowych. Z uwagi na jego umiejscowienie (klauzula odpowiedzialnościowa, nie czysta klauzula dozwolonego użytku) **nie traktuję go jako samodzielnego, rozstrzygającego zakazu** i nie opieram na nim wniosku. (Poprzednia wersja dokumentu cytowała to zdanie jako ogólny zakaz — to było nadużycie i zostało usunięte.)
  - *„Our Commercial Terms of Service govern your use of any Anthropic API key, the Anthropic Console, or any other Anthropic offerings that reference the Commercial Terms.”*

### 4.4. Wniosek prawny (fail-closed)

- Dla **projektowanej** architektury (wariant A/B) źródła są jednoznaczne: niedozwolone bez uprzedniej zgody; zalecany klucz API w ramach Commercial Terms.
- Dla hipotetycznego wariantu **(C)** istnieje warunkowy wyjątek, ale jego spełnienie dla tego projektu (jednoosobowe, nienadzorowane, osadzone w produkcie; „ordinary, individual usage”; brak pośredniczenia) **nie jest potwierdzone** przez sprawdzone źródła.
- §3.7 Consumer Terms dopuszcza dostęp automatyczny **tylko** przez klucz API lub przy wyraźnym zezwoleniu — dla subskrypcji żaden wyjątek nie jest potwierdzony.

Ponieważ dla żadnego wariantu subskrypcyjnego dopuszczalność dla tego konkretnego zastosowania nie została **potwierdzona** (jest albo wprost niedozwolona, albo nierozstrzygnięta), zgodnie z regułą fail-closed wynik to `no-go`: **dopuszczalność niepotwierdzona**, a nie „zakazane w ogólności / wyłącznie API”.

## 5. Rekomendacja go/no-go

**REKOMENDACJA: `no-go` — dopuszczalność niepotwierdzona (fail-closed).**

Dotyczy badanej w pierwszej kolejności ścieżki subskrypcyjnej (Claude Pro/Max przez Claude Code / Agent SDK, logowanie subskrypcyjne, hosting Railway, tryb nienadzorowany). Technicznie strumieniowanie jest wykonalne (sekcja 3), ale **dopuszczalność tej ścieżki dla tego zastosowania nie jest potwierdzona** (sekcja 4): projektowany wariant (A/B) jest przez źródła niedozwolony bez uprzedniej zgody, a warunkowy wyjątek (C) nie został potwierdzony jako spełniony. Zgodnie z regułą fail-closed całość to `no-go`. Nie jest to orzeczenie, że subskrypcja jest „zakazana w każdym przypadku” ani że „dozwolony jest wyłącznie klucz API” — to stwierdzenie braku potwierdzonego pozwolenia dla tej trasy.

Konsekwencje:

- **T-012 pozostaje `blocked`.** Ten spike z wynikiem `no-go` **nie autoryzuje** T-012 ani żadnego adaptera realnego dostawcy.
- Zakończenie spike’u wynikiem `no-go` jest dopuszczalne i kompletne — spike spełnił swój cel (rozstrzygnięcie).
- **Brak automatycznego przejścia na płatne API.** Istnieje ścieżka, którą źródła wprost wskazują jako właściwą dla „developers building products or services” — klucz API Anthropic w ramach Commercial Terms — ale jej wybór jest **decyzją właściciela** (D3), a nie automatycznym fallbackiem tego spike’u. Dopóki właściciel jej nie podejmie i nie zatwierdzi warunków z sekcji 6, stan pozostaje fail-closed.

## 6. Warunki wymagane przed odblokowaniem T-012

T-012 wolno odblokować dopiero, gdy **wszystkie** poniższe są spełnione i jawnie zatwierdzone przez właściciela:

1. **Wybór ścieżki dostępu (D3).** Ścieżką, którą źródła wprost wskazują dla aplikacji („developers building products or services”), jest **klucz API Anthropic w ramach Commercial Terms of Service** — i tylko ta jest potwierdzona jako dopuszczalna. Warianty subskrypcyjne (A/B z sekcji 4.1) są przez źródła niedozwolone bez uprzedniej zgody; warunkowy wyjątek (C) nie jest potwierdzony jako spełniony dla tego projektu. Jeśli właściciel chce którejkolwiek ścieżki subskrypcyjnej, warunkiem jest **pisemna, indywidualna zgoda Anthropic** (sales/contact) potwierdzająca dopuszczalność konkretnego zastosowania.
2. **Akceptacja Commercial Terms** przez właściciela jako posiadacza klucza; użycie billowane na właściciela, bez odsprzedaży/pośredniczenia.
3. **Sekret tylko w zmiennych środowiskowych Railway** (zgodnie ze `SCOPE.md` i `CONVENTIONS.md`); brak klucza w repo, logach, opisach PR; `.env.example` tylko z nazwami.
4. **Miesięczny limit kosztu (cost cap)** ustalony liczbowo i egzekwowany (sekcja 6.1), z zachowaniem reguły „brak automatycznego fallbacku” — wyczerpanie limitu kończy strumień błędem, nie podmienia dostawcy.
5. **Potwierdzenie modelu i trybu** (np. najtańszy model do krótkich promptów) oraz re-weryfikacja cennika i warunków w dniu startu T-012 (warunki mogą się zmienić; patrz daty w sekcji 7).
6. **Weryfikacja semantyki anulowania po stronie dostawcy.** Przed T-012 potwierdzić (dokumentacja lub kontrolowany pomiar w ramach dozwolonej ścieżki), czy zamknięcie połączenia SSE rzeczywiście zatrzymuje generowanie i naliczanie u dostawcy. Do czasu potwierdzenia przyjmować, że koszt może być naliczony za już rozpoczęte generowanie mimo anulowania (sekcja 3.3), i uwzględnić to w limicie kosztu.
7. **Zgoda na ścieżkę testową bez realnego dostawcy** — testy T-012 używają zaślepki HTTP/mocka (zgodnie z treścią T-012 i `CONVENTIONS.md`).

### 6.1. Rekomendowane decyzje i limit kosztu (bez gwarancji prawnych i bez zmyślonych cen)

- **Decyzja D3 (rekomendacja):** jeśli funkcja AI ma powstać, wybrać **klucz API w ramach Commercial Terms** (ścieżka potwierdzona jako dopuszczalna); nie korzystać z żadnej ścieżki subskrypcyjnej bez pisemnej, indywidualnej zgody Anthropic (dopuszczalność niepotwierdzona — patrz sekcja 4).
- **Mechanizm limitu:** ustawić własny limit wydatku w Claude Console (strona Billing) poniżej pułapu tieru; przy nowej organizacji spodziewać się startu w niższym tierze (Evaluation/Start). Pułapy tierów wg dokumentacji: Start $500, Build $1 000, Scale $200 000 (2026-11-02).
- **Proponowany cost cap (do decyzji właściciela):** rzędu kilku–kilkunastu USD/miesiąc wystarcza dla jednoosobowego, okazjonalnego użycia (kilka wywołań tygodniowo, krótkie prompty). To rekomendacja ostrożnościowa, nie wymóg Anthropic.
- **Ceny API (oficjalny cennik, 2026-11-02, USD, za 1 mln tokenów; do re-weryfikacji w dniu startu T-012):**
  - Haiku 5.5: wejście $0,10 (prompty ≤100K) / $0,50 (>100K); wyjście $0,50 / $2,50.
  - Sonnet 5.5: wejście $2; wyjście $10.
  - Opus 5.5: wejście $4; wyjście $20.
  - Dla krótkich promptów puent realny koszt miesięczny jest znikomy względem powyższych pułapów; dokładna kwota zależy od liczby i długości wywołań — **traktować jako szacunek, nie zobowiązanie**.
- **Czego nie potwierdzam:** nie składam gwarancji prawnych ani obietnicy zerowego kosztu; ceny subskrypcji (Pro, Max) pomijam, bo ścieżka subskrypcyjna ma status `no-go` (dopuszczalność niepotwierdzona) i jej wybór i tak wymagałby odrębnej, pisemnej zgody Anthropic.

## 7. Źródła (oficjalne, pierwotne)

Wszystkie sprawdzone 2026-11-02 (odczyt). Daty w nawiasie to data wejścia/aktualizacji podana na stronie, jeśli była.

| # | Źródło | URL | Data na stronie |
|---|--------|-----|-----------------|
| 1 | Claude Code — Legal and compliance (uwierzytelnianie, OAuth vs API key) | https://code.claude.com/docs/en/legal-and-compliance | — |
| 2 | Claude Agent SDK — Overview (zakaz claude.ai login dla produktów; Commercial Terms) | https://code.claude.com/docs/en/agent-sdk/overview | — |
| 3 | Support — „Use Claude Code with your Pro, Max, or Ultra plan” (API key vs subskrypcja, limity) | https://support.claude.com/en/articles/11145838 | „Updated this week” |
| 4 | Consumer Terms of Service | https://www.anthropic.com/legal/consumer-terms | 2025-10-08 |
| 5 | Commercial Terms of Service (ścieżka dozwolona dla API; „not for consumer use”) | https://www.anthropic.com/legal/commercial-terms | 2025-06-17 |
| 6 | Streaming messages (SSE, typy zdarzeń, error events) | https://platform.claude.com/docs/en/build-with-claude/streaming | — |
| 7 | Claude API errors (kody, kształt błędu, błędy w strumieniu, długie żądania) | https://platform.claude.com/docs/en/api/errors | — |
| 8 | Rate limits (RPM/ITPM/OTPM, spend caps, tiery, nagłówki) | https://platform.claude.com/docs/en/api/rate-limits | — |
| 9 | Cennik (ceny API i subskrypcji) | https://claude.com/pricing | — |

Uwaga: warunki i ceny dostawcy mogą się zmienić po dacie sprawdzenia. Przed startem T-012 należy ponownie zweryfikować źródła 1–5 i 9.
