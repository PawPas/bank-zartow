# CI i ochrona brancha `main`

Workflow `.github/workflows/ci.yml` uruchamia `make test` dla każdego pull requesta i dla każdego pusha do `main`. Check nazywa się **`make test`**.

## Ustawienie ochrony brancha (wykonuje właściciel, jednorazowo)

Agenci nie mają uprawnień do ustawień repozytorium.

1. GitHub → repozytorium → **Settings** → **Rules** → **Rulesets** → **New branch ruleset**.
2. Nazwa: `main`. Enforcement status: **Active**.
3. Target branches: **Include default branch**.
4. Zaznacz reguły:
   - **Restrict deletions**
   - **Block force pushes**
   - **Require a pull request before merging** (liczba wymaganych akceptacji: 0, bo review robi agent, a nie człowiek z kontem GitHub)
   - **Require status checks to pass** → dodaj check **`make test`**
5. Zapisz.

Check `make test` pojawi się na liście do wyboru dopiero po pierwszym przebiegu workflow.

## Auto-merge

Po ustawieniu ochrony brancha włącz **Settings** → **General** → **Allow auto-merge** oraz **Allow squash merging**. Wtedy pull request z włączonym auto-merge scali się sam, gdy check `make test` będzie zielony.

Uwaga: w prywatnych repozytoriach na darmowym planie GitHub rulesety i wymagane checki mogą być niedostępne. W takim przypadku reguła „zielony `make test` przed merge'em” pozostaje zasadą z `AGENTS.md`, której pilnuje reviewer, dopóki repozytorium nie zostanie upublicznione albo plan zmieniony.
