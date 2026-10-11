---
id: T-005
epic: E0
title: Jedna usługa, `make run` i Dockerfile
status: todo
depends_on: [T-003, T-004]
manual_review: true
---

## Cel
Całość uruchamia się jedną komendą lokalnie i buduje do jednego obrazu produkcyjnego.

## Zakres
- Spring Boot serwuje zbudowany frontend jako zasoby statyczne
- odświeżenie strony pod adresem frontendu nie zwraca 404
- `make run`: baza z Docker Compose plus aplikacja
- wieloetapowy `Dockerfile` (build frontendu, build backendu, lekki obraz uruchomieniowy)
- ograniczenie pamięci JVM ustawiane zmienną środowiskową

## Poza zakresem
- konfiguracja Railway, logowanie

## Kryteria akceptacji
- [ ] na czystym komputerze z Dockerem `make run` uruchamia aplikację i strona startowa jest dostępna w przeglądarce
- [ ] `docker build .` kończy się sukcesem, a kontener ze wskazaną bazą zwraca `UP` w healthchecku
- [ ] obraz nie zawiera kodu źródłowego ani narzędzi budowania

## Testy
- test integracyjny: strona startowa i zasoby statyczne są serwowane przez backend

## Blokada
