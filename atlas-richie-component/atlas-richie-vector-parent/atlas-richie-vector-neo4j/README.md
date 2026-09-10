# Neo4j Provider

Neo4j's vector/full-text procedures do not provide a safe general filter pushdown contract for this use case. The provider therefore uses ACL-first parameterized Cypher candidate queries for both dense similarity and lexical content matching, followed by Core weighted RRF. It deliberately favors authorization correctness over procedure-index shortcutting.

`hybrid-enabled` creates/validates the required vector, full-text and ACL property indexes, while a dense-only Store remains unchanged. The operation requires explicit structured ACL and never post-filters candidates. Local Neo4j E2E passed denied-node exclusion and deletion (`Neo4jAclSafeHybridLiveIT`).
