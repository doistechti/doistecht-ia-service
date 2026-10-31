-- API key criptografada (AES-GCM) para o portal poder exibi-la de novo. A autenticação continua pelo hash.
-- Clientes anteriores ficam com NULL até gerarem uma nova chave.
ALTER TABLE client ADD COLUMN api_key_encrypted VARCHAR(255);
