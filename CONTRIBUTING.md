# Contributing to TxScope

Thank you for your interest in TxScope.

The project is still in early development. The current focus is delivering a solid MVP around four reproducible failure scenarios (S1–S4) and a clear facts / hypotheses report.

## Before you start

1. Open an **issue** describing the change you want to make.
2. Wait for feedback so we can keep the scope focused.
3. Keep changes small and related to the stated goals of the MVP.

Large refactors, new database engines, or general APM features are out of scope for the first public release.

## Development setup

```bash
# Start PostgreSQL
docker compose up -d

# Run the demo application
mvn spring-boot:run -pl demo-app
```

See the main [README](README.md) for how to reproduce Scenario S1.

## Code guidelines

- Java 21+
- Prefer clarity over cleverness
- Separate **facts** from **hypotheses** in any diagnostic logic
- Do not collect SQL bind parameters
- Keep database access read-only whenever possible
- Add or update a scenario script when you change failure behaviour

## Pull requests

- Reference the related issue
- Describe what was changed and how to test it
- Keep the PR focused on one concern
- Ensure the demo still starts and S1 still saturates the pool

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
