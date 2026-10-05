# migrations

Liquibase changelogs for the **`ecommerce`** database — the schema shared by
every business service (product-service and the services that follow it).

Migrations live here, at the repo root, and not inside the services: one
database has to have one migration history, otherwise two services adding a
column to the same table cannot see each other's changes.

`keycloak` is **not** managed here. It ships its own Liquibase changelogs and
builds its ~90 tables itself on first boot, in its own database (see
`app/init/create-db.sh`). Migrating it from the outside would fight its
internal upgrade path.

## Layout

```
migrations/
├── db.changelog-master.xml          index — the include order IS the run order
└── changelog/
    ├── 20260821133150-create-table-category.sql
    ├── 20260821133232-create-table-supplier.sql
    ├── 20260821133244-create-table-product.sql
    ├── 20260827164811-create-table-purchase-order.sql
    ├── 20260827164845-create-table-purchase-order-detail.sql
    ├── 20260827164856-create-table-purchase-return.sql
    ├── 20260827164912-create-table-purchase-return-detail.sql
    ├── 20260911170928-create-table-sales-order.sql
    ├── 20260911170937-create-table-sales-order-detail.sql
    ├── 20260911171527-create-table-stock.sql
    ├── 20260911171536-create-table-stock-position.sql
    └── 20260911171550-create-function-generate-doc-no.sql
```

The changesets are plain `.sql` files — Liquibase "formatted SQL", where the
`--liquibase formatted sql` header and the `--changeset` markers turn ordinary
SQL into a changelog. Only the master index is XML. The schema therefore reads
as the SQL it actually runs, at the cost of being Postgres-specific and of
having to spell out its own rollback.

## Running

`./build.sh` migrates automatically once Postgres is healthy. By hand:

```sh
./app/init/migrate.sh                  # apply everything that is pending
./app/init/migrate.sh status           # what is pending, nothing applied
./app/init/migrate.sh history          # what has already been applied
./app/init/migrate.sh validate         # check the changelog for problems
./app/init/migrate.sh updateSQL        # print the SQL instead of running it
./app/init/migrate.sh rollbackCount 1  # undo the last changeset
```

Liquibase runs in a throwaway container, so nothing needs installing. See the
header of `app/init/migrate.sh` for the environment variables it reads
(credentials, host, image, `LIQUIBASE_RUNNER=local` to use a host binary).

## Adding a migration

Use the generator at the repo root — it names the file, writes the template and
registers the `<include>` for you:

```sh
./create-migration.sh                      # asks for the name
./create-migration.sh "create table order" # or takes it as an argument
```

The name is slugified and prefixed with a `yyyyMMddHHmmss` timestamp, so
`"Create Table Order"` becomes `changelog/20260821143012-create-table-order.sql`.
The `<include>` is always **appended**, never inserted: Liquibase records what
has run in `DATABASECHANGELOG`, and a reordered history no longer matches it.

Then fill in the SQL and its rollback, and apply:

```sh
./app/init/migrate.sh updateSQL   # preview the SQL
./app/init/migrate.sh            # apply
```

The generated template is header and markers only — the body is yours to write,
and it has to be written before the first `update`. A changeset whose body is
only comments is perfectly valid to Liquibase: it is recorded as EXECUTED having
done nothing, and adding the real SQL afterwards then fails on the changed
checksum. Fill it in, or leave the file out of the master changelog until you
do.

Conventions the existing files follow:

- **One changeset per logical change**, with a stable `id` — a changeset is
  identified by `filename::id::author`, so renaming any of the three makes
  Liquibase treat it as new and run it again.
- **Never edit an applied changeset.** Its checksum is stored; a change makes
  the next `update` fail. Write a new changeset instead.
- **`CREATE ... IF NOT EXISTS`**, so the changelog can also be applied to a
  database that was already migrated by hand (an existing dev volume) without
  failing on a clash.
- **A `--rollback` line for every changeset.** Formatted SQL has no automatic
  reverse — a changeset without one cannot be rolled back at all. This is what
  the old `.down.sql` files became.
- **One `--changeset author:id` per logical change.** `--comment:` above the
  statements documents it; both are Liquibase directives, while a plain `--`
  line is passed through as an ordinary SQL comment.
- **Never rename a changelog file that has already been applied**, and never
  edit an applied changeset. A changeset is identified by
  `filename::id::author` and its body is checksummed, so either one makes
  Liquibase see a new changeset (and re-run it) or fail on the checksum. Add a
  new migration instead. If a rename has already happened, repair the history
  with `./app/init/migrate.sh changelogSync` after clearing the stale rows.
