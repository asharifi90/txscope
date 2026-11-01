# Security

## Report endpoint

- Path: `GET /internal/txscope/report`
- **Disabled by default** (`txscope.report-enabled=false`)
- Enable explicitly only when access is controlled (network policy, Spring Security, management port, etc.)
- Do not expose this endpoint on a public internet-facing URL without authentication

## Data collection

- TxScope does not intentionally collect SQL bind parameters
- Prefer a least-privilege DB role for the monitor pool (read-only session views where possible)
- The monitor pool is bounded (size 1) with short connection/query timeouts

## Reporting vulnerabilities

Please open a GitHub issue marked as security-sensitive, or contact the maintainer privately.
Do not file public issues with exploit details that could harm running deployments.