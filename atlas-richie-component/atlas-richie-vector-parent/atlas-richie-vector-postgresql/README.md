# PostgreSQL / pgvector Provider

Hybrid is opt-in through a generated `tsvector` column and GIN index beside the pgvector embedding. The provider runs parameterized dense KNN and lexical candidate SQL with the same JSONB ACL `WHERE` predicate, then applies Core weighted RRF. ACL values are bound parameters, never interpolated DSL.

Existing dense tables remain compatible until hybrid is explicitly enabled; the setup adds the text/ACL indexes only for that Store. No sparse encoder is required because PostgreSQL produces the lexical branch. The local pgvector E2E passed tenant isolation, lexical and vector candidates, and deletion (`PostgresqlAclSafeHybridLiveIT`).
