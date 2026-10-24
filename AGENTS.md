# Instrukcja dla agentów

Ten plik czyta każdy agent przed rozpoczęciem pracy. Każda sesja startuje z czystym kontekstem, więc pamięcią projektu są pliki w repozytorium, a nie wcześniejsze rozmowy.

## Co przeczytać najpierw

1. `SCOPE.md`: cel, epiki, kryteria akceptacji, rzeczy poza zakresem.
2. `CONVENTIONS.md`: stack, struktura kodu, testy, git.
3. `tasks/README.md`: format taska i zasady wyboru następnego.

## Zasady wspólne

- `SCOPE.md`, kryteria akceptacji w taskach i ten plik zmienia wyłącznie właściciel.
- Jeden task to jeden branch i jeden pull request. Nikt nie pushuje bezpośrednio do `main`.
- Zielony `make test` jest warunkiem koniecznym merge'a.
- Sekretów nie ma w repozytorium i agent ich nie potrzebuje. Jeśli task wydaje się ich wymagać, to jest wyjątek.
- Wyjątek zgłasza się przez ustawienie w tasku `status: blocked` z opisem w sekcji „Blokada” i zakończenie pracy. Nie zgaduj i nie obchodź problemu.

## Rola: programista

1. Wybierz task zgodnie z `tasks/README.md`.
2. Utwórz branch `task/T-XXX-krotki-opis`.
3. Zaimplementuj dokładnie zakres taska. Testy z sekcji „Testy” są częścią zadania.
4. Uruchom `make test`. Czerwony wynik naprawiasz przed otwarciem PR.
5. W tym samym PR ustaw w pliku taska `status: done`.
6. Przed otwarciem PR i przed każdym pushem poprawek zrób rebase brancha na aktualny main.
7. Otwórz PR z tytułem `T-XXX: tytuł taska` i opisem: co zrobiono, jak sprawdzono, lista kryteriów akceptacji z odhaczeniem.
8. Po uwagach z review popraw kod w tym samym branchu. Limit to 3 rundy poprawek, potem task dostaje `status: blocked`.

## Rola: reviewer

Reviewer ocenia wyłącznie diff i plik taska. Nie widzi i nie potrzebuje rozumowania programisty.

Sprawdź po kolei:

1. Czy każde kryterium akceptacji taska jest spełnione i pokryte testem.
2. Czy diff nie wychodzi poza zakres taska ani poza `SCOPE.md`.
3. Czy kod trzyma się `CONVENTIONS.md` (w tym migracje Flyway i brak sekretów).
4. Czy testy sprawdzają zachowanie, a nie tylko się uruchamiają.

Werdykt podaj w komentarzu do PR w tym formacie:

```
VERDICT: approve
```

albo

```
VERDICT: changes_requested
1. plik:linia: co jest nie tak i jakie kryterium narusza
2. ...
```

Uwagi mają być konkretne i wykonalne. Preferencje stylistyczne bez oparcia w `CONVENTIONS.md` nie są podstawą do odrzucenia.

## Kiedy decyduje właściciel

- zatwierdzenie zakresu i listy epików
- taski z `manual_review: true`
- taski ze `status: blocked`
- wygląd plansz (punkt kontrolny w epiku 2)

## Praca z kontekstem i gałęziami

- Każdy nowy task zaczynaj w czystej sesji. Nie przenoś rozumowania ani stanu poprzedniego taska.
- Czytaj selektywnie: `AGENTS.md`, `CONVENTIONS.md`, `tasks/README.md`, odpowiedni zakres `SCOPE.md` i wybrany task. Pozostałe taski czytaj tylko dla potrzebnego kontekstu zależności, nie wszystkie ich treści.
- Jeśli istnieje indeks statusów, używaj go jako pomocy. Źródłem prawdy są pliki tasków na aktualnym `main` oraz bieżąca lista otwartych PR.
- Przed otwarciem PR i każdym pushem poprawek wykonaj fetch i rebase na aktualny `origin/main`. Po zmianie bazy ponownie uruchom `make test` i `make test-fast`.
- Po przepisaniu historii używaj `--force-with-lease`, nigdy `--force`. Jeśli historia nie została przepisana, wykonaj zwykły push.
- W PR i przy zlecaniu review podaj SHA HEAD, komendy i wyniki lokalnych testów oraz stan checków CI dla tego SHA. Brak lub oczekujące checki zgłaszaj wprost; starsze zielone wyniki nie potwierdzają nowego HEAD.
- `manual_review: true` oznacza zakaz merge i włączania auto-merge przez agenta. Decyzję podejmuje właściciel.
- Brak dostępu, sekretu, środowiska lub wymaganej decyzji zgłaszaj jako `status: blocked` z konkretną sekcją „Blokada”. Nie zgaduj i nie obchodź ograniczeń.
- Poświadczenia dostarczaj wyłącznie przez zmienne środowiskowe lub oficjalny mechanizm dostawcy. Nie umieszczaj sekretów w repo, logach ani opisach PR.
- Gdy preferowany dostęp do modelu jest niedostępny lub niezweryfikowany, nie przełączaj się automatycznie na płatne API. Poproś właściciela o decyzję; nie obiecuj zerowego kosztu ani gwarantowanego zatrzymania naliczania.

## Narzędzia i sandbox

- Codex zawsze uruchamiaj z profilem: `codex -p review` do review albo `codex -p auto` do pisania kodu. Nigdy bez profilu.
- Claude Code i Codex nie wychodzą poza sandbox ani nie osłabiają go bez uprzedniej zgody właściciela na Telegramie.
- Jedyny stały wyjątek w `bank-zartow`: `make test` z dostępem do Dockera, w tym pobranie obrazu PostgreSQL. Każda inna eskalacja nadal wymaga odrębnej zgody.
