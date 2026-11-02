# Wdrożenie i konfiguracja

Aplikacja jest publicznie dostępna i bez zalogowania nie pokazuje żadnych danych.
Jest jedno konto, a jego dane pochodzą wyłącznie ze zmiennych środowiskowych. W
repozytorium nie ma żadnych sekretów.

Hosting to Railway: jedna usługa aplikacji (obraz budowany z `Dockerfile`) oraz
usługa PostgreSQL. Budowę i healthcheck opisuje plik `railway.json` w repozytorium
(builder `DOCKERFILE`, healthcheck `/actuator/health`). Wdrożenie następuje
automatycznie przez merge do `main` i integrację Railway z GitHubem.

> Wszystkie kroki w panelu Railway (utworzenie projektu, dodanie usług, ustawienie
> zmiennych, podłączenie repo) wykonuje **właściciel**. Agenci nie mają dostępu do
> konta Railway ani do sekretów.

## Wdrożenie na Railway (właściciel)

1. **Projekt.** Utwórz nowy projekt w Railway.
2. **Baza.** Dodaj usługę PostgreSQL (`+ New` → `Database` → `PostgreSQL`). Railway
   sam nadaje jej zmienne `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`
   oraz `DATABASE_URL`. Zapamiętaj nazwę usługi (domyślnie `Postgres`) — będzie
   potrzebna w referencjach poniżej.
3. **Aplikacja.** Dodaj usługę z repozytorium GitHub (`+ New` → `GitHub Repo` i
   wskaż to repo). Railway wykryje `railway.json` i zbuduje obraz z `Dockerfile`.
   Ustaw deploy z gałęzi `main`.
4. **Zmienne aplikacji.** W usłudze aplikacji ustaw zmienne z sekcji
   [Zmienne środowiskowe](#zmienne-środowiskowe). Dane bazy podłącz referencjami do
   usługi PostgreSQL (patrz [Mapowanie zmiennych bazy](#mapowanie-zmiennych-bazy)).
5. **Adres publiczny.** W usłudze aplikacji wygeneruj domenę (`Settings` →
   `Networking` → `Generate Domain`). Railway wstawi zmienną `PORT`, a aplikacja
   nasłuchuje na niej automatycznie — `PORT` ustawiasz ręcznie **tylko** gdy
   używasz własnego target portu.
6. **Weryfikacja.** Po pierwszym deployu otwórz publiczny adres: aplikacja odpowiada,
   ekran logowania jest widoczny, a bez zalogowania żadne dane nie są dostępne.

## Zmienne środowiskowe

Pełna lista nazw jest też w `.env.example` (nazwy bez wartości). Wymagane zmienne
muszą być ustawione, bo bez nich aplikacja nie wystartuje.

| Zmienna | Wymagana | Opis |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | tak | JDBC URL bazy, np. `jdbc:postgresql://host:5432/db`. Na Railway budowany z referencji do usługi PostgreSQL. |
| `SPRING_DATASOURCE_USERNAME` | tak | Użytkownik bazy. |
| `SPRING_DATASOURCE_PASSWORD` | tak | Hasło bazy. |
| `APP_AUTH_USERNAME` | tak | Login jedynego użytkownika. |
| `APP_AUTH_PASSWORD_HASH` | tak | Hash hasła w formacie bcrypt (nie hasło jawne). Patrz [generowanie hasha](#jak-wygenerować-hash-hasła-bcrypt). |
| `APP_SESSION_COOKIE_SECURE` | na produkcji | `true` na produkcji (HTTPS), `false`/brak dla lokalnego HTTP. |
| `PORT` | nie | Port HTTP. Na Railway wstrzykiwany automatycznie; domyślnie `8080`. Ręcznie tylko przy własnym target porcie. |
| `JAVA_OPTS` | nie | Opcje JVM, np. limit pamięci `-XX:MaxRAMPercentage=75.0` (domyślne w `Dockerfile`). |
| `VITE_API_BASE_URL` | nie | Adres API dla frontendu w dev. Pusty = ten sam origin (produkcja). |

Bez `SPRING_DATASOURCE_*` oraz `APP_AUTH_USERNAME` i `APP_AUTH_PASSWORD_HASH`
aplikacja nie wystartuje — to celowe, żeby produkcja nigdy nie działała bez bazy i
bez skonfigurowanego logowania. Dane logowania są wiązane wprost ze środowiska (bez
pośredniczących placeholderów w `application.properties`), więc brak którejkolwiek z
nich to wartość `null`, którą walidacja odrzuca na starcie.

Ciasteczko sesji dostaje flagę `Secure` tylko wtedy, gdy ustawisz
`APP_SESSION_COOKIE_SECURE=true`. Na produkcji (Railway, HTTPS) ustaw ją na `true`
— inaczej ciasteczko sesji pójdzie bez flagi `Secure`. Domyślnie (brak zmiennej)
jest `false`, co jest poprawne dla lokalnego HTTP.

Na produkcji (Railway) zmienne ustawia właściciel w panelu usługi. Lokalnie
kopiujesz `.env.example` do `.env` i uzupełniasz wartości; `.env` jest w
`.gitignore` i nigdy nie trafia do repozytorium.

## Mapowanie zmiennych bazy

Usługa PostgreSQL na Railway udostępnia `DATABASE_URL` w formacie
`postgresql://user:hasło@host:port/baza`. Spring Boot oczekuje w
`SPRING_DATASOURCE_URL` adresu JDBC (`jdbc:postgresql://...`), więc `DATABASE_URL`
nie nadaje się do bezpośredniego użycia. W usłudze aplikacji ustaw zmienne,
składając JDBC URL z pojedynczych zmiennych bazy przez referencje Railway
(`${{Nazwa.ZMIENNA}}`; podmień `Postgres` na faktyczną nazwę usługi bazy):

```
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
```

Referencje do usługi bazy w tym samym projekcie łączą się wewnątrz sieci Railway, więc
poświadczenia bazy nie są nigdzie wpisywane ręcznie ani wystawiane publicznie.

## Jak wygenerować hash hasła (bcrypt)

`APP_AUTH_PASSWORD_HASH` to hash bcrypt (np. `$2b$...`, `$2y$...` lub `$2a$...`).
Wybierz jedną z metod i wklej **cały** wynik jako wartość zmiennej. W obu
wariantach hasło wpisujesz interaktywnie, bez echa — nie trafia do argumentów
procesu ani do historii powłoki.

### Wariant A: `htpasswd` (pakiet apache2-utils / httpd-tools)

```bash
htpasswd -nBC 12 "" | cut -d: -f2
```

`-B` wymusza bcrypt, `-C 12` to koszt (liczba rund). Bez flagi `-b` narzędzie
pyta o hasło (dwa razy, bez wyświetlania). Pustą nazwę użytkownika (`""`) odcinamy
przez `cut`, bo potrzebujemy samego hasha.

### Wariant B: Python z biblioteką `bcrypt`

```bash
python3 -c 'import bcrypt, getpass; print(bcrypt.hashpw(getpass.getpass().encode(), bcrypt.gensalt(12)).decode())'
```

`getpass.getpass()` czyta hasło bez echa i bez umieszczania go w linii poleceń.

### Uwagi

- Hash wklejamy w całości, razem z prefiksem `$2...`. Aplikacja sama dokleja
  wewnętrzny znacznik `{bcrypt}`, więc podajesz czysty hash z `htpasswd`/`bcrypt`.
- Hasła jawnego nie podajemy w argumentach polecenia (widoczne w `ps` i w historii
  powłoki); używamy wyłącznie interaktywnego, nieechowanego wejścia jak wyżej.
- Hasła jawnego nigdzie nie zapisujemy — ani w repo, ani w logach, ani w opisie PR.
- Zmiana hasła to po prostu nowy hash w zmiennej środowiskowej i restart usługi.

## Kopie zapasowe bazy (właściciel)

Kopie zapasowe włącza się w usłudze PostgreSQL: zakładka `Backups` w ustawieniach
usługi. Railway oferuje harmonogramy, które można łączyć:

- `Daily` — kopia co 24 h, przechowywana 6 dni,
- `Weekly` — kopia co 7 dni, przechowywana 27 dni,
- `Monthly` — kopia co 30 dni, przechowywana 89 dni.

Dla pilota wystarczy `Daily`. Przywrócenie: w zakładce `Backups` wybierz kopię po
dacie, kliknij `Restore`, przejrzyj zmianę na kanwie projektu i zatwierdź `Deploy`.
Railway montuje przywrócone dane jako nowy wolumen i zachowuje poprzedni odłączony.

## Healthcheck i wdrożenie bez przerwy

`railway.json` ustawia `healthcheckPath` na `/actuator/health` (endpoint zdrowia
Spring Boot) oraz `healthcheckTimeout` na 300 s. Przy nowym deployu Railway odpytuje
ten adres i dopiero po odpowiedzi `2x` (status `UP`) przełącza ruch na nową wersję.
Jeśli healthcheck nie przejdzie w oknie czasu, deploy jest oznaczany jako nieudany, a
dotychczasowa działająca wersja nadal obsługuje ruch — nieudana wersja jej nie
zastępuje. `restartPolicyType` `ON_FAILURE` dodatkowo restartuje usługę, gdy proces
padnie już po starcie.
