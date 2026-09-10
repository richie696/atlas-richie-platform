# Qdrant Provider

Qdrant hybrid is opt-in. A hybrid Store creates named dense and sparse vectors plus indexed ACL payload fields. Its write path upserts both vector forms for the same point and ID deletion removes both forms.

`VectorAclAwareHybridSearchOperations` executes two Qdrant recalls: dense and sparse. The identical typed `VectorFilter` is mapped to the Qdrant gRPC filter and attached to both recalls before candidate selection; Core then applies weighted RRF. It is therefore `ACL_SAFE_HYBRID` with `execution=core-rrf`, not a claim of server-native fusion.

Enable `hybrid-enabled`, provide `sparse-vectorizer`, and declare ACL payload fields/indexes. Dense-only remains the default. The operation rejects a missing ACL filter and never post-filters broad candidates. Local Qdrant E2E has passed for write, ACL negative case, fusion and delete; see `QdrantAclSafeHybridLiveIT`.
