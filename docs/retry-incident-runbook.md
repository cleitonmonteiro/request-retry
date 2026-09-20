# Retry incident runbook

1. Compare first-attempt success, retry success, exhausted, budget-denied, circuit-open, and
   unknown-outcome rates by controlled operation name.
2. If retries amplify load, disable automatic retry for the affected profile and keep status
   reconciliation enabled for already-created commands.
3. Never clear pending Create Order records or generate replacement keys during mitigation.
4. Confirm backend idempotency/status health before closing the incident.
5. Roll back only to a version that can read the current Room schema and recognize operations
   created by the new protocol.

Dashboard, alert thresholds, on-call ownership, and feature-flag commands must be filled in by the
production SRE/backend teams before rollout.
