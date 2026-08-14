-- Demo API client for local/manual testing. Raw key: "demo-key-12345" (see README).
-- api_key_hash = SHA-256(raw key), hex-encoded.
INSERT INTO core.api_clients (api_key_hash, name, status)
VALUES ('367fe8933ad8bba8f7ff02c047bcb5c00a4fff3ad6e82fef2bf4ee0c850d7c36', 'demo-client', 'ACTIVE');
