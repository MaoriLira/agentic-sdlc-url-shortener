# URL Shortener — Prototype

Full setup, run, and test instructions are at the repository root:
**[`../README.md`](../README.md)**

Full architectural documentation is the knowledge graph, starting at
**[`../docs/00-Executive-Summary.md`](../docs/00-Executive-Summary.md)**.

Quick start (see the root README for the full explanation of each step):

```bash
# from the repository root
docker-compose up -d

# from here (project/)
DB_PORT=5435 ./mvnw spring-boot:run
```

Use `./mvnw`, not a locally-installed `mvn` — it's pinned to the exact Maven version this
project was built and verified against.
