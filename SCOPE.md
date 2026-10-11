# SCOPE: Bank żartów (pilot)

Status: **do zatwierdzenia** (bramka 1)
Wersja: 0.3, 2026-10-06

## 1. Cel

Narzędzie dla jednej osoby do prowadzenia humorystycznych postów na Instagramie: zapisywanie pomysłów na żarty, przerabianie ich na gotowe plansze i sprawdzanie, które rodzaje humoru odbiorcy najchętniej udostępniają.

Drugi cel pilota: przetestować zautomatyzowany workflow (planista, programista, reviewer, koordynator) na małym, zamkniętym projekcie.

## 2. Użytkownik i sposób użycia

Jeden użytkownik, głównie na telefonie (przeglądarka), czasem na komputerze.

Typowy obieg: żart wpada do głowy i jest zapisywany na telefonie. W weekend użytkownik wybiera kilka najlepszych, dopracowuje puenty i eksportuje plansze. Publikuje ręcznie. Po publikacji dopisuje wyniki, które zostają przy konkretnym żarcie.

## 3. Stack i założenia techniczne

- Backend: Java, Spring Boot, PostgreSQL, migracje Flyway
- Frontend: React, układ projektowany najpierw pod telefon
- Baza lokalnie w Docker Compose, testy integracyjne na Testcontainers
- Hosting: Railway, jedna usługa aplikacji (Spring Boot serwuje zbudowany frontend React) plus usługa PostgreSQL
- Wdrożenie automatyczne z gałęzi `main` przez integrację Railway z GitHubem, obraz budowany z `Dockerfile` w repo
- Sekrety (hasło, klucz AI, dane bazy) wyłącznie w zmiennych środowiskowych Railway
- Dostęp chroniony logowaniem jednego użytkownika (Spring Security)
- Komendy wspólne dla ludzi i agentów: `make run`, `make test`, `make test-fast`

## 4. Epiki

### Epik 0: Szkielet projektu

Zakres:
- repozytorium z backendem i frontendem, Docker Compose z bazą
- `make run`, `make test` (pełny zestaw), `make test-fast` (tylko jednostkowe)
- po jednym przykładowym teście backendu, frontendu i integracyjnym
- plik `CONVENTIONS.md`: struktura pakietów, nazewnictwo, styl testów, zasady migracji
- `Dockerfile`, endpoint healthcheck, logowanie jednego użytkownika
- pierwsze wdrożenie na Railway (konfigurację projektu i zmienne ustawia właściciel)

Kryteria akceptacji:
- na czystym komputerze z Dockerem `make run` uruchamia aplikację z pustą stroną startową
- `make test` przechodzi na zielono
- aplikacja odpowiada pod publicznym adresem Railway i bez zalogowania nie pokazuje żadnych danych
- nieudane wdrożenie (czerwony healthcheck) nie zastępuje działającej wersji
- epik przeglądany ręcznie przez właściciela (wyjątek od automatycznego merge)

### Epik 1: Bank żartów

Zakres:
- szybkie dodawanie żartu z telefonu (minimum: sama treść)
- tagi, np. absurd, gry słowne, limeryk (dowolne, tworzone przez użytkownika)
- statusy: pomysł, do poprawy, gotowy, opublikowany
- zapis alternatywnych puent do jednego żartu, z oznaczeniem wybranej
- lista z filtrowaniem po tagu i statusie, edycja i usuwanie

Kryteria akceptacji:
- dodanie żartu z telefonu wymaga najwyżej dwóch dotknięć poza wpisaniem treści
- żart może mieć wiele tagów i wiele puent, dokładnie jedna puenta jest wybrana
- filtry po tagu i statusie działają łącznie
- dane przetrwają restart aplikacji

### Epik 2: Przygotowanie posta

Zakres:
- dwa szablony: pojedyncza plansza oraz karuzela "wprowadzenie → puenta"
- podgląd na żywo i edycja tekstu na planszy
- eksport do plików graficznych gotowych do publikacji
- przycisk AI: "skróć wprowadzenie" oraz "zaproponuj 3 inne puenty", zawsze z zachowaniem oryginału

Kryteria akceptacji:
- eksport tworzy pliki PNG 1080 × 1350 px (pion 4:5), karuzela jako osobne pliki w kolejności
- plansza: jasne tło, ciemny tekst w jednym kroju wyrównany do lewej, podpis profilu na dole; w karuzeli numer slajdu, strzałka na pierwszym slajdzie i puenta większym pismem
- tekst nie wychodzi poza planszę, zbyt długi jest zmniejszany lub sygnalizowany
- propozycje AI trafiają do alternatywnych puent, nic nie jest nadpisywane bez decyzji użytkownika
- w testach wywołania AI są zamockowane, klucz API jest poza repozytorium
- wygląd plansz zatwierdza właściciel na podstawie przykładowych eksportów (punkt kontrolny wysyłany na telefon)

### Epik 3: Kolejka i wyniki

Zakres:
- prosty plan tygodnia: przypisanie daty do gotowego żartu
- oznaczenie publikacji i zapis linku do posta
- ręczne wpisanie zasięgu, udostępnień i zapisów, np. po 48 godzinach
- ranking żartów i tagów według udostępnień na 1000 odbiorców

Kryteria akceptacji:
- żart ze statusem "gotowy" można przypisać do dnia, widok tygodnia pokazuje plan
- oznaczenie publikacji zmienia status na "opublikowany"
- ranking liczy udostępnienia / zasięg × 1000 i pomija posty bez wpisanego zasięgu
- ranking dostępny per żart i per tag

## 5. Poza zakresem

- wielu użytkowników, role, rejestracja
- automatyczna publikacja na Instagramie
- pobieranie statystyk z API Instagrama
- aplikacja natywna na telefon
- środowisko staging, własna domena
- generowanie żartów od zera przez AI

## 6. Kolejność

Epik 0 → 1 → 2 → 3. Po epikach 1 i 2 narzędzie jest już użyteczne, epik 3 domyka obieg od pomysłu do oceny publikacji.

## 7. Decyzje do podjęcia przy zatwierdzeniu

- **D1. Format plansz.** Rozstrzygnięte: 1080 × 1350 px (pion 4:5).
- **D2. Logowanie.** Rozstrzygnięte: aplikacja wystawiona na Railway, jedno konto z hasłem ustawianym w zmiennych środowiskowych.
- **D3. Dostawca AI dla przycisku w edytorze.** Do wskazania, wraz z miesięcznym limitem kosztu.
- **D4. Wygląd szablonów.** Rozstrzygnięte: jasne tło, jeden krój pisma, bez grafik i bez wariantu ciemnego w pilocie.

## 8. Zasady dla agentów

- Ten plik i kryteria akceptacji zmienia wyłącznie właściciel.
- Jeśli task wymaga czegoś z sekcji 5 albo sprzecznego z kryteriami, agent przerywa pracę i zgłasza wyjątek.
- Każdy task kończy się zielonym `make test`.
- Agenci nie mają dostępu do konta Railway ani do sekretów. Wdrożenie następuje wyłącznie przez merge do `main`.
- Każda zmiana schematu bazy to nowa migracja Flyway. Istniejących migracji się nie edytuje.
