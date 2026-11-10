# StudioMesa — endurecimento da leitura binária de documentos

**Data:** 10 de novembro de 2026  
**Âmbito:** cliente Android/Supabase de documentos privados de fornecedores.

## Intenção

Impedir que uma resposta inesperadamente grande do Storage seja integralmente carregada para a memória do processo Android antes de o limite do documento ser verificado. A app já restringe os uploads a 25 MiB e o bucket alojado também tem um limite de 25 MiB, mas o download atual só verifica o tamanho depois de `readBytes()`.

## Objetivos

- Aplicar o limite de 25 MiB durante a leitura do stream, não depois.
- Rejeitar de forma controlada respostas cujo `Content-Length` anunciado exceda o limite.
- Limitar também os corpos de erro do Storage, para que um proxy/servidor não consiga devolver uma resposta arbitrariamente grande.
- Preservar a leitura atual de JSON, a autenticação, o cabeçalho `Cache-Control: no-store` e o comportamento de erros.
- Testar limite exato, excesso de um byte, stream nulo e streams que devolvam zero numa leitura em bloco.

## Segurança e privacidade

A leitura continua a usar HTTPS, a chave publicável e o token da sessão autenticada. As políticas Storage atuais limitam o bucket `studiomesa-invoices` ao proprietário do restaurante. Esta alteração não muda permissões nem migrações e não envia conteúdo para serviços externos.

## Abordagens consideradas

1. **Verificar apenas Content-Length:** rejeita respostas com tamanho anunciado excessivo, mas não protege contra servidores que omitem ou mentem sobre esse cabeçalho.
2. **Ler todo o corpo e verificar depois:** é o comportamento atual e não protege a memória.
3. **Leitura incremental limitada, com verificação antecipada de Content-Length:** rejeita corpos anunciados acima do limite e interrompe a leitura assim que o stream ultrapassa o máximo. É a opção escolhida porque protege também quando o cabeçalho está ausente ou incorreto.

## Fora do âmbito

- Reconciliação das migrações remotas do Supabase, alterações em Auth/Storage ou `db push`.
- Revogação em massa de funções `SECURITY DEFINER`.
- Configuração de SMTP/OAuth, assinatura comercial, política legal ou publicação.
- Garantir cifragem ponta a ponta para dados sincronizados na cloud; a implementação atual usa autenticação/RLS, não E2E.

## Critérios de aceitação

- O teste unitário aceita exatamente 25 MiB e rejeita 25 MiB + 1 byte.
- A leitura limitada funciona sem Content-Length e em streams que retornam zero numa leitura em bloco.
- Os testes Android já existentes e a CI completa (cinco variantes, unit tests, Lint, migrations/RLS, Android 8 e Android 16) passam.
- A alteração fica numa PR separada; não é integrada automaticamente na `main`.
