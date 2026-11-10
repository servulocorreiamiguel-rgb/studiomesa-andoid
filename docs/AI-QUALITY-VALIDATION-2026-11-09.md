# StudioMesa — validação de qualidade da IA

**Data:** 9 de novembro de 2026  
**Branch de trabalho:** `feature/assistant-free-quality-hardening-2026-11-09`  
**Modelo atual:** LFM2.5-1.2B-Instruct INT4, LiteRT-LM, inferência local  
**Objetivo:** melhorar a utilidade sem API generativa paga e distinguir testes automatizados de testes reais no dispositivo.

## Alterações desta revisão

- Adicionada a ferramenta local `calculate_exact_math`, com operações `+`, `-`, `*`, `/`, parênteses e números decimais com vírgula ou ponto.
- A calculadora rejeita divisão por zero, sintaxe desconhecida, mais de 64 operações, expressões acima de 256 caracteres, precisão acima de 12 casas decimais e resultados fora do intervalo suportado.
- O assistente geral e o Gestor Económico recebem a ferramenta; as instruções mandam usá-la em vez de fazer contas mentalmente.
- A memória contextual aumentou de 200 para 350 caracteres, permitindo incluir uma nota estável completa de até 220 caracteres.

## Testes automatizados introduzidos

| ID | Teste | Resultado esperado |
|---|---|---|
| MATH-01 | `2 + 3 * 4` | 14, respeitando a prioridade da multiplicação |
| MATH-02 | `(2 + 3) * 4` | 20 |
| MATH-03 | `1 / 3` | 0,333333333333 |
| MATH-04 | `1,5 + 2,25` | 3,75 |
| MATH-05 | Divisão por zero, sintaxe inválida, operadores não suportados | Rejeição controlada, sem crash |
| MATH-06 | Entrada demasiado longa, mais de 12 casas decimais, valor excessivo ou mais de 64 operações | Rejeição controlada |
| MEMORY-01 | Nota estável com 220 caracteres | Nota completa preservada no contexto |
| TOOL-01 | Chamada Android a `calculate_exact_math` | Resultado determinístico e `read_only=true` |

## Testes funcionais ainda necessários no OPPO A5m

Os testes seguintes não podem ser declarados aprovados por uma compilação ou por testes unitários. Exigem que o modelo esteja instalado e que a aplicação seja executada no telemóvel físico.

| ID | Cenário | Critério de aprovação |
|---|---|---|
| DEVICE-01 | Instalação/verificação do modelo | Transferência completa, SHA-256 correto e motor inicializado |
| DEVICE-02 | Pergunta geral em português europeu | Resposta relevante, sem desviar para temas de restauração |
| DEVICE-03 | Matemática escolar | Resultado correto; ferramenta de cálculo usada quando aplicável |
| DEVICE-04 | Seguimento em várias mensagens | “Faz mais curto”, “explica o passo seguinte” e referências ao texto anterior preservam o contexto |
| DEVICE-05 | Memória | Preferência estável explicitamente indicada reaparece numa conversa nova do mesmo restaurante |
| DEVICE-06 | Modo Pensar mais | Resposta mais cuidadosa sem alegar acesso a raciocínio privado |
| DEVICE-07 | Ferramentas do restaurante | Só usa valores efetivamente carregados e atualizados; não inventa stock, pedidos ou reservas |
| DEVICE-08 | Análise semanal/previsão | Distingue valor nominal, linhas de pedidos e pagamentos; sem histórico comparável não inventa previsão |
| DEVICE-09 | Feriados/eventos | Reconhece feriados nacionais; identifica Carnaval como facultativo e eventos locais como configuração do proprietário |
| DEVICE-10 | Fatura anonimizada | Extrai campos candidatos, assinala incerteza e exige revisão humana; nunca aprova ou paga |
| DEVICE-11 | Sincronização em dois dispositivos | Só sincroniza após consentimento; alterações e eliminações convergem; testar também perda e reposição da rede |
| DEVICE-12 | Privacidade | Pesquisa pública não recebe contactos, NIF, IBAN, faturas, histórico ou dados privados |
| DEVICE-13 | Desempenho | Registar tempo até ao primeiro token, tempo total, memória, temperatura e consumo de bateria em respostas curtas e longas |

### Registo obrigatório por execução física

Anotar o commit instalado, variante, versão Android, espaço livre, estado da rede, se o modelo já estava carregado em memória, tempos observados, temperatura antes/depois e resultado de cada ID. Usar contas e documentos exclusivamente descartáveis/anónimos.

## Limites de custo e capacidade

A inferência generativa continua local e não exige uma API paga nem uma cobrança por mensagem. A primeira transferência do modelo consome cerca de 736 MB de dados e ocupa armazenamento no telemóvel.

Isto não torna o modelo equivalente ao ChatGPT Free. O LFM2.5-1.2B é muito menor, tem contexto limitado a cerca de 4 096 tokens e pode falhar tarefas complexas. A calculadora e as ferramentas determinísticas melhoram a precisão em operações específicas, mas não substituem um modelo de maior capacidade. Não se deve anunciar equivalência ao ChatGPT.

## Estado de validação

- Os testes unitários e a integração Android desta revisão ficam sujeitos à CI do respetivo commit.
- A CI de Android/emuladores verifica compilação, lógica local instrumentada e fluxos de interface, mas não mede a velocidade nem a qualidade real do modelo no OPPO A5m.
- Todos os testes DEVICE-01 a DEVICE-13 permanecem pendentes até serem executados e registados no dispositivo físico.
