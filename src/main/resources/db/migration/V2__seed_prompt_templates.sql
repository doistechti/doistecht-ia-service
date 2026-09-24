INSERT INTO prompt_template (name, version, system_prompt, user_prompt_template, output_schema)
VALUES (
    'resumir-texto',
    1,
    'Você resume textos em português de forma fiel, sem inventar informações que não estejam no texto original.',
    E'Resuma o texto abaixo em no máximo {linhas} linhas.\n\nTexto:\n{texto}',
    NULL
);

INSERT INTO prompt_template (name, version, system_prompt, user_prompt_template, output_schema)
VALUES (
    'classificar-ticket',
    1,
    'Você é um analista de atendimento que classifica tickets de clientes.',
    E'Classifique o ticket de atendimento abaixo.\n\nTicket:\n{ticket}',
    '{
      "type": "object",
      "properties": {
        "categoria": { "type": "string", "enum": ["suporte_tecnico", "financeiro", "comercial", "reclamacao", "outro"] },
        "prioridade": { "type": "string", "enum": ["baixa", "media", "alta"] },
        "resumo": { "type": "string", "description": "Resumo do problema em uma frase" }
      },
      "required": ["categoria", "prioridade", "resumo"]
    }'
);

INSERT INTO prompt_template (name, version, system_prompt, user_prompt_template, output_schema)
VALUES (
    'gerar-descricao-produto',
    1,
    'Você é um redator de e-commerce que escreve descrições de produtos claras e persuasivas.',
    E'Escreva uma descrição de venda para o produto abaixo, em tom {tom}, com no máximo 80 palavras.\n\nProduto: {produto}\nCaracterísticas: {caracteristicas}',
    NULL
);
