# Wdrożenie i konfiguracja

Aplikacja jest publicznie dostępna i bez zalogowania nie pokazuje żadnych danych.
Jest jedno konto, a jego dane pochodzą wyłącznie ze zmiennych środowiskowych. W
repozytorium nie ma żadnych sekretów.

## Zmienne środowiskowe

Pełna lista nazw jest w `.env.example`. Zmienne istotne dla logowania:

| Zmienna | Opis |
| --- | --- |
| `APP_AUTH_USERNAME` | Login jedynego użytkownika. |
| `APP_AUTH_PASSWORD_HASH` | Hash hasła w formacie bcrypt (nie hasło jawne). |
| `APP_SESSION_COOKIE_SECURE` | `true` na produkcji (HTTPS), `false` dla lokalnego HTTP. |

Bez `APP_AUTH_USERNAME` i `APP_AUTH_PASSWORD_HASH` aplikacja nie wystartuje — to
celowe, żeby produkcja nigdy nie działała bez skonfigurowanego logowania. Zmienne
są wiązane wprost ze środowiska (bez pośredniczących placeholderów w
`application.properties`), więc brak którejkolwiek z nich to wartość `null`, którą
walidacja odrzuca na starcie.

Ciasteczko sesji dostaje flagę `Secure` tylko wtedy, gdy ustawisz
`APP_SESSION_COOKIE_SECURE=true`. Na produkcji (Railway, HTTPS) ustaw ją na `true`
— inaczej ciasteczko sesji pójdzie bez flagi `Secure`. Domyślnie (brak zmiennej)
jest `false`, co jest poprawne dla lokalnego HTTP.

Na produkcji (Railway) zmienne ustawia właściciel w panelu usługi. Lokalnie
kopiujesz `.env.example` do `.env` i uzupełniasz wartości; `.env` jest w
`.gitignore` i nigdy nie trafia do repozytorium.

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
