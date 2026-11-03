# Gemini gratuito no Assistente StudioMesa

## Estado da integração

A branch inclui um caminho opcional para Gemini 3.8 Flash através da Edge Function restaurant-assistant. A aplicação Android nunca contém a chave Gemini. A função exige uma sessão Supabase válida e confirma que o utilizador é proprietário do restaurante.

O proprietário tem de ativar explicitamente a opção no ecrã do Assistente. O aviso informa que o escalão gratuito permite que a Google use pedidos e respostas para melhorar os produtos. O pedido não inclui anexos, texto OCR/XML, Memória nem dados de faturas.

O filtro local bloqueia conversas que contenham termos fiscais/financeiros sensíveis, números, valores monetários, endereços de email ou números de telefone. É uma proteção heurística, não uma garantia absoluta: o aviso ao proprietário continua obrigatório.

## Configuração do servidor

1. Criar uma chave de API no Google AI Studio: https://aistudio.google.com/app/apikey
2. Configurar GEMINI_API_KEY nos Secrets das Edge Functions do projeto Supabase. Não colocar a chave no Android, no GitHub, em ficheiros de configuração ou no chat.
3. Opcionalmente configurar GEMINI_MODEL; por omissão usa gemini-3.8-flash.
4. Deploy da função restaurant-assistant e teste autenticado como proprietário.

A API Gemini indica tokens gratuitos em modelos elegíveis, sujeitos a limites; no escalão gratuito o conteúdo pode ser usado para melhorar produtos Google. O serviço pode recusar pedidos quando atingir limites. Não há garantia de utilização ilimitada.

## Limites

- A IA generativa só está disponível para proprietários autenticados de um restaurante configurado.
- Perguntas sobre faturas, IVA, fornecedores, valores, despesas, dados pessoais e números ficam no motor local.
- O filtro é conservador mas não substitui a prudência do utilizador nem DLP garantido.
- O limite de 10 pedidos por minuto implementado na Edge Function é best-effort por instância; os limites do fornecedor continuam a ser a autoridade.
- A integração não autoriza o modelo a alterar preços, pedidos, reservas, stock ou permissões.
