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

### 3.3. Anulowanie

- Strumień to długo żyjące połączenie HTTP SSE; zamknięcie połączenia przez klienta przerywa odbiór. W modelu Reactor anulowanie subskrypcji `Flux` zamyka połączenie i zatrzymuje dalsze zdarzenia, co odwzorowuje wymóg T-009 (anulowanie zatrzymuje generowanie) i T-010 (rozłączenie klienta propaguje `cancel`). Dokumentacja zaleca strumieniowanie właśnie dla długich żądań i opisuje keep-alive/TCP socket options dla połączeń długotrwałych (Claude API errors — „Long requests”, 2026-11-02).

### 3.4. Limity i pojemność (dotyczą API, nie subskrypcji)

- Dwa rodzaje limitów: **spend limits** (miesięczny pułap kosztu) oraz **rate limits** (RPM / input-tokens-per-minute / output-tokens-per-minute), zależne od **usage tier** (Rate limits, 2026-11-02).
- Miesięczne pułapy wydatku wg tieru: **Start $500**, **Build $1 000**, **Scale $200 000**; Custom bez pułapu. Można też ustawić **własny** limit wydatku poniżej pułapu tieru (w Console, strona Billing).
- Po osiągnięciu pułapu żądania zwracają `429 rate_limit_error`; przy pułapie tieru brak nagłówka `retry-after` (ponawianie nie pomaga do końca miesiąca), a przy własnym ustawionym limicie zwracany jest `400 invalid_request_error`.
- Nowe organizacje mogą startować w **Evaluation tier** z limitami niższymi niż standardowe. Nagłówki `anthropic-ratelimit-*` i `retry-after` pozwalają reagować na limity.

## 4. Ustalenia prawne / warunki dostawcy

Wniosek prawny dla badanej ścieżki (subskrypcja Pro/Max przez Claude Code/Agent SDK w hostowanej, nienadzorowanej aplikacji): **niedozwolona — `no-go`.** Poniżej dowody ze źródeł pierwotnych.

### 4.1. Dokumentacja Claude Code — uwierzytelnianie i użycie poświadczeń

Strona „Legal and compliance” Claude Code (2026-11-02) stwierdza wprost:

- *„OAuth authentication is intended exclusively for purchasers of Claude Free, Pro, Max, Ultra, Team, and Enterprise subscription plans and is designed to support ordinary use of Claude Code and other native Anthropic applications.”* — subskrypcyjny OAuth jest przeznaczony do **zwykłego użycia Claude Code i natywnych aplikacji Anthropic**, a nie do zasilania własnej aplikacji.
- *„Developers building products or services that interact with Claude's capabilities, including those using the Agent SDK, should use API key authentication through Claude Console or a supported cloud provider. Anthropic does not permit third-party developers to … route requests through Free, Pro, Max, or Ultra plan credentials on behalf of their users.”*
- *„Advertised usage limits for Pro, Max, and Ultra plans assume ordinary, individual usage of Claude Code and the Agent SDK.”* — reklamowane limity zakładają zwykłe, indywidualne użycie, a nie nienadzorowaną usługę serwerową.
- *„Anthropic reserves the right to take measures to enforce these restrictions and may do so without prior notice.”*

Carve-out ze strony (platforma może hostować **niemodyfikowany binarny Claude Code**, do którego użytkownik loguje się własną subskrypcją) **nie obejmuje** naszego przypadku: bank żartów nie uruchamia niemodyfikowanego Claude Code jako narzędzia dla użytkownika, tylko wbudowuje zdolności Claude w autorską funkcję produktu.

### 4.2. Dokumentacja Agent SDK

Strona „Agent SDK overview” (2026-11-02):

- *„Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login or rate limits for their products, including agents built on the Claude Agent SDK. Use the API key authentication methods … instead.”*
- *„Use of the Claude Agent SDK is governed by Anthropic's Commercial Terms of Service, including when you use it to power products and services that you make available to your own customers and end users.”*

### 4.3. Consumer Terms of Service (regulamin subskrypcji), data wejścia: 2025-10-08

- Regulamin konsumencki rządzi *„Claude.ai, Claude Pro, and other products and services that we may offer for individuals”*; użycie API reguluje osobny Commercial Terms.
- *„You agree that you will not use our Services for any commercial or business purposes.”*
- Zakaz dostępu *„through automated or non-human means, whether through a bot, script, or otherwise”* — z wyjątkiem dostępu przez klucz API lub wyraźne pozwolenie.
- *„Our Commercial Terms of Service govern your use of any Anthropic API key, the Anthropic Console, or any other Anthropic offerings that reference the Commercial Terms.”*

### 4.4. Wniosek prawny (fail-closed)

Nienadzorowana, hostowana na Railway usługa wołająca model z backendu to dostęp „automated/non-human” i wbudowanie zdolności Claude w autorski produkt. Dla takiego użycia wszystkie trzy źródła pierwotne kierują na **klucz API w ramach Commercial Terms**, a ścieżki subskrypcyjnej (OAuth Pro/Max przez Claude Code/Agent SDK) **wprost nie dopuszczają** bez odrębnej zgody Anthropic. Ani jeden wymagany punkt dopuszczalności ścieżki subskrypcyjnej nie został potwierdzony. Fakt, że użytkownik jest jeden i że to „własna” subskrypcja, nie znosi zakazu: Consumer Terms zakazują użycia komercyjnego/biznesowego i dostępu automatycznego, a dokumentacja Claude Code/Agent SDK zastrzega OAuth do zwykłego, indywidualnego użycia natywnych aplikacji.

## 5. Rekomendacja go/no-go

**REKOMENDACJA: `no-go`.**

Dotyczy badanej w pierwszej kolejności ścieżki subskrypcyjnej (Claude Pro/Max przez Claude Code / Agent SDK, logowanie subskrypcyjne, hosting Railway, tryb nienadzorowany). Technicznie strumieniowanie jest wykonalne (sekcja 3), ale **warunki dostawcy tej ścieżki nie dopuszczają** (sekcja 4), więc zgodnie z regułą fail-closed całość to `no-go`.

Konsekwencje:

- **T-012 pozostaje `blocked`.** Ten spike z wynikiem `no-go` **nie autoryzuje** T-012 ani żadnego adaptera realnego dostawcy.
- Zakończenie spike’u wynikiem `no-go` jest dopuszczalne i kompletne — spike spełnił swój cel (rozstrzygnięcie).
- **Brak automatycznego przejścia na płatne API.** Istnieje terms-compliant alternatywa (klucz API Anthropic w ramach Commercial Terms), ale jej wybór jest **decyzją właściciela** (D3), a nie automatycznym fallbackiem tego spike’u. Dopóki właściciel jej nie podejmie i nie zatwierdzi warunków z sekcji 6, stan pozostaje fail-closed.

## 6. Warunki wymagane przed odblokowaniem T-012

T-012 wolno odblokować dopiero, gdy **wszystkie** poniższe są spełnione i jawnie zatwierdzone przez właściciela:

1. **Wybór dozwolonej ścieżki dostępu (D3).** Jedyna potwierdzona jako dopuszczalna to **klucz API Anthropic w ramach Commercial Terms of Service** (nie subskrypcja Pro/Max, nie OAuth Claude Code/Agent SDK). Alternatywnie: pisemna, indywidualna zgoda Anthropic na inną ścieżkę (sales/contact).
2. **Akceptacja Commercial Terms** przez właściciela jako posiadacza klucza; użycie billowane na właściciela, bez odsprzedaży/pośredniczenia.
3. **Sekret tylko w zmiennych środowiskowych Railway** (zgodnie ze `SCOPE.md` i `CONVENTIONS.md`); brak klucza w repo, logach, opisach PR; `.env.example` tylko z nazwami.
4. **Miesięczny limit kosztu (cost cap)** ustalony liczbowo i egzekwowany (sekcja 6.1), z zachowaniem reguły „brak automatycznego fallbacku” — wyczerpanie limitu kończy strumień błędem, nie podmienia dostawcy.
5. **Potwierdzenie modelu i trybu** (np. najtańszy model do krótkich promptów) oraz re-weryfikacja cennika i warunków w dniu startu T-012 (warunki mogą się zmienić; patrz daty w sekcji 7).
6. **Zgoda na ścieżkę testową bez realnego dostawcy** — testy T-012 używają zaślepki HTTP/mocka (zgodnie z treścią T-012 i `CONVENTIONS.md`).

### 6.1. Rekomendowane decyzje i limit kosztu (bez gwarancji prawnych i bez zmyślonych cen)

- **Decyzja D3 (rekomendacja):** jeśli funkcja AI ma powstać, wybrać **klucz API w ramach Commercial Terms**; **odrzucić** ścieżkę subskrypcyjną jako niedopuszczalną.
- **Mechanizm limitu:** ustawić własny limit wydatku w Claude Console (strona Billing) poniżej pułapu tieru; przy nowej organizacji spodziewać się startu w niższym tierze (Evaluation/Start). Pułapy tierów wg dokumentacji: Start $500, Build $1 000, Scale $200 000 (2026-11-02).
- **Proponowany cost cap (do decyzji właściciela):** rzędu kilku–kilkunastu USD/miesiąc wystarcza dla jednoosobowego, okazjonalnego użycia (kilka wywołań tygodniowo, krótkie prompty). To rekomendacja ostrożnościowa, nie wymóg Anthropic.
- **Ceny API (oficjalny cennik, 2026-11-02, USD, za 1 mln tokenów; do re-weryfikacji w dniu startu T-012):**
  - Haiku 5.5: wejście $0,10 (prompty ≤100K) / $0,50 (>100K); wyjście $0,50 / $2,50.
  - Sonnet 5.5: wejście $2; wyjście $10.
  - Opus 5.5: wejście $4; wyjście $20.
  - Dla krótkich promptów puent realny koszt miesięczny jest znikomy względem powyższych pułapów; dokładna kwota zależy od liczby i długości wywołań — **traktować jako szacunek, nie zobowiązanie**.
- **Czego nie potwierdzam:** nie składam gwarancji prawnych ani obietnicy zerowego kosztu; ewentualne ceny subskrypcji (Pro, Max) są dla tej ścieżki bez znaczenia, bo ścieżka subskrypcyjna jest `no-go`.

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
