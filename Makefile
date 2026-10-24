.PHONY: test-fast test

# Unit tests only (*Test, frontend unit tests). No Docker required.
test-fast:
	cd backend && ./mvnw -B test
	cd frontend && npm ci && npm run test

# Full suite: unit tests plus integration tests (*IT) and the production
# frontend build.
test:
	cd backend && ./mvnw -B verify
	cd frontend && npm ci && npm run test && npm run build
