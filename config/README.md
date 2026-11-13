# Canonical configuration

The reviewed JSON files define care, hydration and Play balance. Corresponding generators live in `tools/balance/`. Run their `--check` mode from the repository root to verify shared constants and migration templates agree.

Changing existing configuration must not overwrite a migration already applied to Supabase. Introduce a new migration when changing deployed gameplay rules.

`examples/supabase.properties.example` contains public configuration names only. Copy the needed names into the root `local.properties` or use environment variables; the example file is not loaded automatically. The mobile app accepts a publishable/legacy anon key, never a secret/service-role key. Keep machine paths, actual local configuration and signing material untracked.

See [setup](../README.md), [authentication](../docs/features/AUTHENTICATION.md) and [database rules](../docs/backend/DATABASE.md).
