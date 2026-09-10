# VikingDB Provider

VikingDB hybrid is available only for `hnsw-hybrid` Stores with declared scalar ACL indexes and a named sparse encoder. The provider maps the common dense/sparse request to the Atlas Richie AI VikingDB plugin's native `HYBRID` request and attaches the mandatory structured filter before execution. It declares `ACL_SAFE_HYBRID` with `execution=native` only after these Store preconditions validate.

Dense-only remains the default. Lifecycle actions additionally require a control-plane endpoint. VikingDB index provisioning is asynchronous: an accepted create request is not treated as a ready-to-query guarantee, and `RESOURCE_NOT_READY` must be retried according to the caller's readiness policy. Real cloud E2E is still pending current environment credentials and fine-grained IAM verification; no live pass is claimed here.
