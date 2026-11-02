---
id: T-017
epic: E1
title: Łączne filtry statusu i tagu oraz paginacja listy
status: todo
depends_on: [T-015, T-016]
manual_review: true
---

## Cel
Dać przeglądalną listę: filtrowanie po statusie i tagu działające łącznie oraz paginację, aby lista pozostała użyteczna na telefonie przy rosnącej liczbie żartów. Realizuje kryterium E1 „filtry po tagu i statusie działają łącznie”.

## Zakres
- `GET /api/jokes?status=&tag=&page=&size=` → filtr po statusie I tagu łącznie (AND); oba parametry opcjonalne
- paginacja: `page` i `size` z rozsądnym rozmiarem domyślnym i górnym limitem; stabilne sortowanie (najnowsze pierwsze)
- odpowiedź zawiera elementy strony oraz metadane (numer strony, rozmiar, całkowita liczba elementów)
- frontend: filtry statusu i tagu na liście oraz nawigacja po stronach, przez klienta API; teksty po polsku

## Poza zakresem
- nowe pola modelu (status pochodzi z T-015, tagi z T-016)
- wyszukiwanie pełnotekstowe po treści

## Kryteria akceptacji
- [ ] filtr po samym statusie zwraca wyłącznie żarty w tym statusie
- [ ] filtr po samym tagu zwraca wyłącznie żarty z tym tagiem (po normalizacji nazwy)
- [ ] podanie obu filtrów działa łącznie (AND) — wynik spełnia oba warunki jednocześnie
- [ ] bez filtrów zwracane są wszystkie żarty, stronami, najnowsze pierwsze
- [ ] paginacja zwraca właściwą stronę i metadane; rozmiar strony jest ograniczony górnym limitem

## Testy
- IT (`*IT`): filtr tylko statusu; tylko tagu; oba łącznie (AND); brak filtrów; granice paginacji (rozmiar, kolejna strona, przekroczenie limitu rozmiaru)
- frontend (Vitest + RTL): ustawienie filtrów i zmiana strony wywołuje klienta API z właściwymi parametrami zapytania

## Blokada
