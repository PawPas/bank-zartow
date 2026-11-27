---
id: T-024
epic: E-ML
title: Rate limiting magic linku bez lockoutu właściciela
status: blocked
depends_on: [T-021, T-022]
manual_review: true
---

## Cel
Ograniczyć spam mailowy i zgadywanie tokenów tak, żeby atakujący nie mógł ani zalać
skrzynki właściciela, ani zablokować mu logowania żadną ścieżką.

## Zakres
- liczniki w pamięci procesu (jedna instancja produkcyjna), okna czasowe
  z konfiguracji, defaulty w kodzie (propozycje do decyzji D-ML4: żądania 5/15 min
  per IP i 3/15 min per adres; potwierdzenia 10/15 min per IP — wszystkie liczby
  odwracalne jedną zmianą konfiguracji)
- czas okien wyłącznie z wstrzykiwanego `Clock` (bean z T-020) — testy przesuwają
  zegar, bez sleepów
- limit per IP (żądania i potwierdzenia): jawny `429` z `ProblemDetail`
- limit per adres (po normalizacji, dla KAŻDEGO podanego adresu): cichy — odpowiedź
  pozostaje `202`, mail nie wychodzi (żaden sygnał nie zdradza, który adres jest
  dozwolony)
- przekroczenie limitów nie unieważnia wydanych tokenów, nie zmniejsza puli
  aktywnych tokenów i nie wpływa na logowanie hasłem
- limity wygasają z upływem okna (brak trwałej blokady)

## Poza zakresem
- trwałe magazyny liczników, blokady kont, captcha
- zmiana polityki puli tokenów (T-020, decyzja D-ML6)

## Kryteria akceptacji
- [ ] po przekroczeniu limitu per IP kolejne żądania dostają 429
- [ ] po przekroczeniu limitu per adres odpowiedź to nadal 202, ale mail nie jest
      wysyłany i token nie jest wydawany
- [ ] zachowanie limitu per adres jest identyczne dla adresu dozwolonego i obcego
- [ ] limit nie unieważnia wcześniej wydanego tokenu (potwierdzenie nadal działa)
- [ ] logowanie hasłem działa niezależnie od wyczerpania limitów magic linku
- [ ] po upływie okna (przesunięcie `Clock`) żądania znów przechodzą

## Testy
- IT (Testcontainers): wszystkie kryteria; okna sterowane wstrzykiwanym `Clock`,
  bez sleepów
- test jednostkowy: logika licznika i okna

## Blokada
Bramka zakresu: epik E-ML nie jest częścią `SCOPE.md`, a `SCOPE.md` zmienia wyłącznie
właściciel. Odblokowanie: właściciel akceptuje `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1) i zmienia status tego taska na `todo`. Do tego czasu implementacji
się nie rozpoczyna.
