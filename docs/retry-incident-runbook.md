# Retry incident runbook

1. Compare first-attempt success, retry success, exhausted attempts, and unknown-outcome rates by
   controlled operation name.
2. If retries amplify load, reduce attempts in the affected operation profile and release the
   change; this study app has no remote kill switch.
3. Confirm backend idempotency and status-query health before closing an incident involving Create
   Order.
4. Never advise a user to submit a replacement order after an ambiguous completion; verify the
   status from the active screen session instead.
