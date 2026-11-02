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
celowe, żeby produkcja nigdy nie działała bez skonfigurowanego logowania.

Na produkcji (Railway) zmienne ustawia właściciel w panelu usługi. Lokalnie
kopiujesz `.env.example` do `.env` i uzupełniasz wartości; `.env` jest w
`.gitignore` i nigdy nie trafia do repozytorium.

## Jak wygenerować hash hasła (bcrypt)

`APP_AUTH_PASSWORD_HASH` to hash bcrypt (np. `$2b$...`, `$2y$...` lub `$2a$...`).
Wybierz jedną z metod i wklej **cały** wynik jako wartość zmiennej.

### Wariant A: `htpasswd` (pakiet apache2-utils / httpd-tools)

```bash
htpasswd -nbBC 12 "" "TWOJE_HASLO" | cut -d: -f2
```

`-B` wymusza bcrypt, `-C 12` to koszt (liczba rund). Pomijamy nazwę użytkownika
(pusty `""`), bo potrzebujemy samego hasha.

### Wariant B: Python z biblioteką `bcrypt`

```bash
python3 -c 'import bcrypt; print(bcrypt.hashpw(b"TWOJE_HASLO", bcrypt.gensalt(12)).decode())'
```

### Uwagi

- Hash wklejamy w całości, razem z prefiksem `$2...`. Aplikacja sama dokleja
  wewnętrzny znacznik `{bcrypt}`, więc podajesz czysty hash z `htpasswd`/`bcrypt`.
- Hasła jawnego nigdzie nie zapisujemy — ani w repo, ani w logach, ani w opisie PR.
- Zmiana hasła to po prostu nowy hash w zmiennej środowiskowej i restart usługi.
