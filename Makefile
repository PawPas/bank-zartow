.PHONY: test-fast test

# Unit tests only (*Test). No Docker required.
test-fast:
	cd backend && ./mvnw -B test

# Full suite: unit tests plus integration tests (*IT).
test:
	cd backend && ./mvnw -B verify
