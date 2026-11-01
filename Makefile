.PHONY: run test-fast test

# Local run: build the frontend, start PostgreSQL via Docker Compose, then run
# the app which serves the built frontend as static resources. Datasource
# credentials and ports are read from the local .env (see .env.example).
run:
	cd frontend && npm ci && npm run build
	docker compose up -d
	set -a; [ -f .env ] && . ./.env; set +a; cd backend && ./mvnw -B spring-boot:run

# Unit tests only (*Test, frontend unit tests). No Docker required.
test-fast:
	cd backend && ./mvnw -B test
	cd frontend && npm ci && npm run test

# Full suite: frontend unit tests and production build first (so the backend can
# bundle it as static resources), then backend unit plus integration tests (*IT).
test:
	cd frontend && npm ci && npm run test && npm run build
	cd backend && ./mvnw -B verify
