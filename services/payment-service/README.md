# payment-service

**Context :** Paiement (Spring Boot · PostgreSQL).

Responsabilités :
- CRUD méthodes de paiement + transactions.
- Intégration **Stripe** et **PayPal** (clés API via Vault, jamais en clair).
- Cascade intra-base : payment_method → transactions ; user_ref → payment_methods.
- Endpoint interne `/internal/by-user/{id}` pour la purge cross-service (cf. §3).

> Postgres choisi pour la criticité ACID (argent). Placeholder Phase 0.