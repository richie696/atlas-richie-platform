# MongoDB Atlas Provider

An opt-in hybrid Store uses Atlas `$vectorSearch` and Atlas Search `$search`. The provider compiles the same structured ACL filter for both pipelines, so unauthorized documents never become dense or lexical candidates; Core weighted RRF then merges the two safe lists.

Hybrid requires a vector-search index, an Atlas Search text index and filterable ACL metadata fields. The Provider maps every declared ACL metadata field as a nested Atlas Search `token` field, so the lexical branch can enforce the same equality/in ACL predicates as the vector branch. Dense-only configuration remains unchanged. Atlas Local E2E passed index readiness, ACL negative behavior and deletion (`MongoDbAtlasAclSafeHybridLiveIT`). Production deployments still need Atlas Search/vector-search availability and corresponding index permissions.
