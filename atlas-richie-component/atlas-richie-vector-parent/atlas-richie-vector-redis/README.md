# Redis Stack Provider

Redis Stack hybrid combines a RediSearch dense KNN branch with a full-text branch. Both queries carry the same compiled TAG/NUMERIC ACL predicate before RediSearch produces candidates, and Core weighted RRF merges only these safe candidates.

No sparse encoder is required because the second branch is the native text index. `hybrid-enabled` is opt-in; dense-only Stores retain their existing schema and behavior. The document mapping keeps content, ACL fields and the dense vector synchronized on upsert/update/delete. Missing ACL input is rejected. Local Redis Stack E2E passed including ACL negative case, duplicate fusion and deletion (`RedisAclSafeHybridLiveIT`).
