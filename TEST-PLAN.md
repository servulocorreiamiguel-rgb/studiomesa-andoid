# Plano de testes — pedidos, quantidades, fases e notas

**Versão-alvo:** StudioMesa 1.4.0 (APK de demonstração)  
**Funcionalidade:** quantidade por artigo, fase de serviço e nota de cozinha por pedido.  
**Estado:** plano preparado; os testes de execução ainda não foram feitos num telemóvel real.

## Ambiente e pré-condições

- Instalar a mesma variante de demonstração em dois dispositivos Android compatíveis.
- Usar uma conta de teste de proprietário e outra de funcionário do mesmo restaurante; criar também uma conta de outro restaurante e uma conta de cliente.
- Confirmar que só existem dados de teste. Não usar nomes, contactos ou pedidos de clientes reais.
- Guardar o commit, a variante instalada e os resultados de cada execução.
- O workflow atual compila variantes debug; uma compilação bem-sucedida não substitui estes testes funcionais.

## Casos de teste

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| ORD-01 | Normal | Criar um pedido com quantidade 1, fase Principal e nota vazia | Pedido guardado e mostrado uma única vez |
| ORD-02 | Normal | Criar um pedido com quantidade 2 e nota curta | Quantidade, nota e total da linha corretos |
| ORD-03 | Normal | Usar cada fase permitida: Bebidas, Aperitivo, Entrada, Principal e Sobremesa | Fase preservada após fechar e reabrir a aplicação |
| ORD-04 | Normal | Alterar o estado do pedido até Concluído | Novo estado refletido no ecrã e no histórico |
| ORD-05 | Normal | Consultar histórico de um pedido | Eventos e horários correspondem às alterações efetuadas |
| ORD-06 | Limite | Quantidade 1 | Aceite |
| ORD-07 | Limite | Quantidade 99 | Aceite |
| ORD-08 | Inválido | Quantidade 0, negativa ou 100 | Rejeitada pelo formulário/servidor; nenhum pedido parcial |
| ORD-09 | Inválido | Fase de serviço não reconhecida | Rejeitada; não guardar um valor alternativo silenciosamente |
| ORD-10 | Limite | Nota com 500 caracteres | Aceite e preservada integralmente |
| ORD-11 | Inválido | Nota com 501 caracteres | Rejeitada com mensagem clara |
| ORD-12 | Inválido | Artigo em branco, mesa inválida ou valor monetário negativo | Rejeitado sem alterar os dados existentes |
| ORD-13 | Permissões | Utilizador sem sessão tenta criar ou alterar pedidos remotos | Acesso recusado; os dados mantêm-se inalterados |
| ORD-14 | Permissões | Funcionário autorizado cria e atualiza um pedido | Operação aceite dentro do restaurante ao qual pertence |
| ORD-15 | Permissões | Conta de outro restaurante tenta consultar ou modificar o pedido | Nenhum dado do primeiro restaurante devolvido ou alterado |
| ORD-16 | Permissões | Cliente abre a aplicação de cliente e consulta a carta | Só vê campos publicados; nunca recebe custos internos |
| ORD-17 | Rede | Desligar a internet antes de guardar | Erro claro; a interface não declara sucesso |
| ORD-18 | Rede | Cortar a ligação durante o envio e voltar a tentar | Verificar se foi criado zero ou um pedido; sem duplicados silenciosos |
| ORD-19 | Rede | Expirar a sessão antes de atualizar o pedido | Renovação controlada ou pedido de novo login; sem perda silenciosa |
| ORD-20 | Sincronização | Dois dispositivos atualizam mesas/pedidos quase ao mesmo tempo | Estado final coerente e sem sobrescritas inesperadas |
| ORD-21 | Regressão | Abrir um pedido antigo criado antes dos novos campos | Valores predefinidos coerentes; a aplicação não falha |
| ORD-22 | Regressão | Reiniciar a aplicação e consultar o pedido | Dados persistem conforme a origem local/remota |

## Execução automatizada disponível

O workflow `.github/workflows/android.yml` executa a compilação das cinco variantes debug. Não foram identificados testes unitários ou testes instrumentados na estrutura inspecionada.

Comando de compilação usado pelo workflow:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

## Registo de resultados desta revisão

| Verificação | Resultado real |
|---|---|
| Revisão das restrições SQL de quantidade/fase/nota | Inspecionada na estrutura da base de dados; não equivale a teste funcional |
| Testes manuais em Android | Não executados nesta sessão |
| Testes unitários/instrumentados | Não disponíveis/identificados na estrutura inspecionada |
| Compilação CI desta alteração visual | A aguardar execução do workflow da pull request |
| Testes de rede e concorrência | Não executados |
| Teste de isolamento entre restaurantes com contas reais | Não executado |

Não marcar um caso como aprovado sem guardar a evidência do teste. Uma compilação aprovada não permite concluir que a app está segura ou pronta para operar um restaurante.
