.PHONY: run test-fast test smoke

# Local run: build the production image and start it together with PostgreSQL via
# Docker Compose. Only Docker is required on the host; the frontend and backend
# are built inside the image. Datasource credentials and ports come from the
# local .env (see .env.example).
run:
	docker compose up --build

# Unit tests only (*Test, frontend unit tests). No Docker required.
test-fast:
	cd backend && ./mvnw -B test
	cd frontend && npm ci && npm run test

# Full suite: frontend unit tests and production build first (so the backend can
# bundle it as static resources), then backend unit plus integration tests (*IT),
# then the production-image smoke test (builds the image, runs the container
# against an ephemeral PostgreSQL, checks the healthcheck and image contents).
test:
	cd frontend && npm ci && npm run test && npm run build
	cd backend && ./mvnw -B verify
	./scripts/smoke.sh

# Build the Docker image and smoke-test the production container end to end.
smoke:
	./scripts/smoke.sh
