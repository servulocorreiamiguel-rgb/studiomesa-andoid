# StudioMesa AI local — chat, memória e modelo generativo offline (03/11/2026)

## Arquitetura atual

O assistente combina duas camadas:

1. **Modelo generativo local:** LFM2.5 1.2B Instruct, quantização INT4, executado no Android através de LiteRT-LM 0.16.1. A inferência decorre no próprio dispositivo; a mensagem, o histórico e os dados selecionados não são enviados para uma API de IA.
2. **Ferramentas determinísticas:** cálculos e validações existentes da StudioMesa continuam disponíveis para preços/custos, stock e operação. O modelo generativo não substitui as fórmulas nem transforma estimativas em valores contabilísticos oficiais.

A integração online com Gemini foi retirada do caminho Android. A Edge Function que foi publicada anteriormente no Supabase não é chamada por esta versão do Android e não é necessária ao modo local.

## Modelo e transferência

- Modelo: `litert-community/LFM2.5-1.2B-Instruct`.
- Ficheiro: `LFM2.5-1.2B-Instruct_int4.litertlm`.
- Tamanho esperado: **736 015 744 bytes** (cerca de 736 MB decimais).
- SHA-256 esperado: `a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906`.
- Origem: [Hugging Face — LiteRT Community](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct).
- Runtime Android: [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), versão `0.16.1`.
- Transferência inicial por HTTPS para a pasta privada da aplicação. O ficheiro temporário só é instalado após validação do tamanho e SHA-256.
- Depois da instalação, a inferência não precisa de Internet e não tem custo por mensagem. A primeira transferência consome aproximadamente 736 MB de dados; recomenda-se Wi-Fi.
- O modelo não é empacotado no APK, para evitar um instalador de tamanho excessivo.

## Execução e limites

- O modelo usa backend CPU por defeito, com contexto máximo configurado de 4096 tokens e até 512 tokens de saída.
- Para controlar o consumo de memória, o pedido inclui até seis mensagens anteriores, com conteúdo limitado, além de um resumo compacto do contexto do restaurante.
- O motor é inicializado numa thread de trabalho, não na thread da interface. A resposta é apresentada por blocos.
- A instância de inferência é libertada quando a Activity deixa de estar visível; o ficheiro do modelo permanece instalado.
- A aplicação mantém a resposta determinística como alternativa enquanto o modelo não for instalado.
- A quantidade de memória usada e a velocidade dependem do dispositivo, da versão Android e da carga do sistema. A compilação não prova que o modelo funcione com desempenho aceitável no OPPO A5m. É obrigatório testar instalação, inicialização, resposta, temperatura, memória e consumo de bateria no aparelho físico antes de o anunciar como compatível.
- Um modelo de 1,2 mil milhões de parâmetros não é equivalente ao ChatGPT Plus. Tem menor capacidade de raciocínio, conhecimento e contexto; também não tem pesquisa web integrada nem conhecimento atualizado por si só.

## Contexto do restaurante

O prompt local pode incluir apenas os dados que a aplicação já tem carregados e os que o utilizador guardou na Memória: nome do restaurante, artigos da carta e respetivos valores registados, totais de pedidos carregados e, quando a cache está válida, stock e número de reservas pendentes.

Não inclui automaticamente contactos de clientes, notas privadas de reservas, documentos, faturas ou dados de outros restaurantes. A leitura de faturas e a sincronização de memória entre dispositivos continuam por implementar.

O modelo pode sugerir, calcular e explicar. Não pode gravar preços, alterar stock, pedidos, reservas, permissões ou pagamentos. Qualquer alteração futura tem de ser executada por um fluxo próprio da aplicação, com autorização e confirmação explícita do utilizador.

## Privacidade

- Mensagens e respostas são guardadas pelo `AssistantChatStore`, cifradas com AES-GCM e chave mantida no Android Keystore.
- O âmbito local do histórico é separado por conta/restaurante.
- A inferência do modelo não faz chamadas de rede. A única comunicação desta funcionalidade é a transferência inicial do ficheiro público do modelo.
- A Memória continua local ao dispositivo; não sincroniza entre dispositivos.
- O modelo não recebe dados bancários nem faturas automaticamente. A leitura de faturas só deve ser acrescentada depois de criar importação explícita, armazenamento seguro, permissões exclusivas do proprietário e revisão de privacidade.

## Licenças e atribuição

- O runtime LiteRT-LM é distribuído sob a licença Apache 2.0: [LICENSE do LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/LICENSE).
- O modelo LFM2.5 usa a LFM Open License v1.0. Esta licença define condições de utilização comercial e de atribuição, incluindo um limiar de receita anual de 10 milhões de dólares para a concessão gratuita indicada pelo licenciante. Antes de uma distribuição comercial, ler e cumprir o texto integral e confirmar se a situação da empresa continua dentro das condições: [LFM Open License](https://www.liquid.ai/lfm-open-license) e [modelo LFM2.5 no Hugging Face](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct).
- A aplicação descarrega o modelo do repositório público em vez de o incluir no APK. A distribuição comercial deve manter estes avisos, disponibilizar as licenças aplicáveis e confirmar as obrigações de atribuição.

## Validação pendente

Antes de declarar esta implementação pronta:

1. Compilar todas as variantes Android com as novas dependências Kotlin/LiteRT-LM.
2. Executar testes unitários, Android Lint, testes instrumentados do histórico e smoke tests em Android 8 e Android 16.
3. Testar transferência interrompida, retomada por nova tentativa, falta de espaço, ficheiro corrompido e hash inválido.
4. Testar inicialização do motor, perguntas sucessivas, mensagens longas, encerramento e libertação de memória.
5. Testar no OPPO A5m real: tempo até primeira resposta, tokens por segundo, RAM, temperatura, bateria e estabilidade numa conversa longa.
6. Testar que modo local não faz pedidos HTTP durante a inferência e que os dados de um restaurante não entram no contexto de outro.
7. Validar legalmente o uso e a atribuição do modelo antes de comercializar a app.

**Estado:** código de integração local criado na branch de desenvolvimento; compilação final e validação física ainda não estão confirmadas. Não distribuir como funcionalidade pronta até esses testes passarem.
