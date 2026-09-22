# ADR-004: Durable critical commands

Status: superseded by the foreground-only study scope.

The earlier pilot persisted Create Order state in Room and reconciled it with unique WorkManager
work. The simplified study app deliberately removes that persistence and background recovery.
Create Order is now scoped to the active foreground session; production durability remains a
separate design problem.
