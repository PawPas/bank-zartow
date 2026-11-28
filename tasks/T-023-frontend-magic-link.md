---
id: T-023
epic: E-ML
title: Frontend magic linku — formularz e-mail i strona potwierdzenia
status: blocked
depends_on: [T-021, T-022]
manual_review: true
---

## Cel
Użytkownik na telefonie wpisuje adres, klika link z maila (także na innym urządzeniu)
i jawnie potwierdza logowanie.

## Zakres
- ekran logowania: obok hasła formularz „wyślij link na e-mail"; po wysłaniu zawsze
  ten sam komunikat („Jeśli adres jest poprawny, wysłaliśmy link…"), niezależnie od
  adresu i wyniku wysyłki
- strona `/login/confirm`: rozpoznanie ścieżki w `App.tsx` (bez wprowadzania routera;
  istniejący fallback SPA w `WebConfig` już ją serwuje); token odczytywany
  z **fragmentu URL** (`location.hash`), po odczycie natychmiast usuwany z paska
  adresu przez `history.replaceState`; przycisk „Zaloguj się" wykonujący POST confirm
- nowe urządzenie: przed POST-em SPA woła istniejący `GET /api/auth/session`
  (dostarcza ciasteczko XSRF) — istniejący wzorzec z `client.ts`
- komunikaty porażki potwierdzenia generyczne („Link jest nieprawidłowy lub wygasł.
  Poproś o nowy."), z przyciskiem powrotu do formularza
- rozszerzenie `frontend/src/api/client.ts` o `requestMagicLink` i `confirmMagicLink`
  (komponenty nie wołają fetch bezpośrednio); teksty UI po polsku

## Poza zakresem
- zmiany w backendzie; usuwanie formularza hasła (decyzja D-ML5)

## Kryteria akceptacji
- [ ] komunikat po wysłaniu formularza jest identyczny dla każdego adresu i wyniku
- [ ] strona potwierdzenia nie wykonuje żadnego żądania zużywającego token przed
      kliknięciem przycisku
- [ ] token jest odczytywany z fragmentu URL i znika z paska adresu po załadowaniu
      strony potwierdzenia
- [ ] kliknięcie potwierdzenia loguje i pokazuje aplikację (stan jak po logowaniu
      hasłem)
- [ ] porażka potwierdzenia pokazuje generyczny komunikat i drogę powrotu do
      formularza

## Testy
- Vitest + RTL: formularz (identyczny komunikat dla różnych adresów i przy błędzie
  sieci); strona potwierdzenia (brak wywołania confirm przed kliknięciem — asercja
  na mock klienta; sukces → aplikacja; porażka → komunikat); odczyt tokenu
  z `location.hash` i wywołanie `replaceState`
- test klienta API: poprawne ścieżki, nagłówek CSRF, mapowanie 401/429

## Blokada
Bramka zakresu: epik E-ML nie jest częścią `SCOPE.md`, a `SCOPE.md` zmienia wyłącznie
właściciel. Odblokowanie: właściciel akceptuje `docs/MAGIC-LINK-SCOPE-PROPOSAL.md`
(decyzja D-ML1) i zmienia status tego taska na `todo`. Do tego czasu implementacji
się nie rozpoczyna.
