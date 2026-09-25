-- Provedor de IA usado por padrão nas chamadas do cliente; nulo usa o padrão global
ALTER TABLE client ADD COLUMN default_provider VARCHAR(30);
