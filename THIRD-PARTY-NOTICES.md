# StudioMesa — avisos de terceiros e inventário de licenças

**Estado:** inventário preliminar da configuração de 6 de novembro de 2026. Este ficheiro não substitui a recolha automática de licenças de todo o grafo Gradle nem a revisão jurídica antes da distribuição.

## Modelo de linguagem

- **Modelo:** LFM2.5-1.2B-Instruct INT4, artefacto `LFM2.5-1.2B-Instruct_int4.litertlm`.
- **Origem do ficheiro:** comunidade `litert-community` no Hugging Face, conversão/quantização do modelo LFM publicado pela Liquid AI.
- **Tamanho esperado:** 736 015 744 bytes.
- **SHA-256 esperado:** `a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906`.
- **Licença indicada pelo repositório do modelo:** LFM Open License v1.0. Esta licença tem condições específicas, incluindo condições ligadas à receita anual da organização e outras obrigações. Não assumir elegibilidade comercial até conhecer a entidade que vai comercializar o produto e confirmar os termos integrais.
- **Referências:** [modelo INT4 e metadados](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct) · [ficheiro de licença](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/blob/main/LICENSE) · [documentação de terceiros do modelo](docs/THIRD-PARTY-AI-MODEL.md).

**Atenção:** o modelo não foi criado nem treinado pela StudioMesa. A conversão comunitária também tem de ser atribuída e as suas condições de redistribuição confirmadas. A mera possibilidade técnica de o descarregar não prova autorização para redistribuir comercialmente.

## Runtime de IA

- **Dependência Android:** `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
- **Projeto:** [Google AI Edge LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM).
- **Licença do projeto indicada no repositório:** Apache License 2.0.
- **Ação para release:** recolher o texto integral da licença e notices da versão/tag realmente utilizada, e verificar licenças de todas as dependências nativas/transitivas incluídas no AAB.

## Dependências declaradas diretamente no módulo Android

| Componente | Versão declarada | Uso | Estado da licença / ação |
|---|---:|---|---|
| `com.google.ai.edge.litertlm:litertlm-android` | 0.18.0 | Runtime de inferência local | O repositório LiteRT-LM indica Apache 2.0; obter o notice da versão exata e rever transitivas. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.2 | Coroutines Kotlin | Maven POM declara Apache-2.0; preservar notice. |
| `com.google.mlkit:text-recognition` | 16.0.1 | OCR de texto local em imagem/PDF renderizado | Artefacto Google ML Kit; metadados Maven consultados não fornecem uma licença OSS explícita. Rever os termos/condições Google aplicáveis e manter como **verificação obrigatória**, não o classificar como Apache/MIT. |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | Leitura/geração de QR | Maven POM declara Apache 2.0; depende de `com.google.zxing:core:3.4.1`, também Apache 2.0 segundo POM. Preservar ambos os notices. |
| `com.android.tools:desugar_jdk_libs` | 2.1.5 | Compatibilidade de APIs Java | Dependência de build/runtime; recolher a licença e os notices do artefacto exato e das transitivas antes de assinar. |
| `junit:junit` | 4.13.2 | Testes unitários | Dependência de teste; recolher licença/notice no SBOM de desenvolvimento. Não deve ser assumido como parte do runtime APK. |
| `androidx.test:core` | 1.7.0 | Testes instrumentados | Dependência de teste. Recolher os notices do grafo de teste. |
| `androidx.test:runner` | 1.7.0 | Testes instrumentados | Dependência de teste. Recolher os notices do grafo de teste. |
| `androidx.test.ext:junit` | 1.3.0 | Testes instrumentados | Dependência de teste. Recolher os notices do grafo de teste. |
| `androidx.test.espresso:espresso-core` | 3.7.0 | Testes instrumentados/UI | Dependência de teste. Recolher os notices do grafo de teste. |

Referências consultadas: [Maven Central — Coroutines 1.10.2](https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core/1.10.2) · [Maven Central — ZXing Embedded 4.3.0](https://central.sonatype.com/artifact/com.journeyapps/zxing-android-embedded/4.3.0) · [Maven Central — ZXing Core 3.4.1](https://central.sonatype.com/artifact/com.google.zxing/core/3.4.1) · [Google AI Edge — LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) · [ML Kit release notes](https://developers.google.com/ml-kit/release-notes).

## Toolchain e dependências de desenvolvimento

- Android Gradle Plugin: 8.10.0.
- Kotlin Android plugin: 2.3.0.
- Gradle wrapper: 8.11.1.
- JDK: Java 17.
- Android SDK/Build Tools: API 36 / Build Tools 36.0.0 nas verificações de release.

Estes componentes são usados para construir/testar a app e não devem ser confundidos com dependências Android de runtime. A sua cadeia de distribuição e uso em CI também deve ser mantida atualizada.

## Serviços externos e conteúdo

- **Supabase:** backend gerido (Auth, PostgreSQL, Storage e Edge Functions). Rever termos, DPA, subprocessadores, região e retenção efetiva.
- **Wikimedia/Wikipedia:** ferramenta opcional de pesquisa envia uma consulta pública, quando acionada. Rever termos/API e atribuição de resultados; não tratar como fonte garantida de informação recente.
- **Hugging Face / litert-community:** origem pública do ficheiro de modelo. Confirmar disponibilidade, integridade, licença e condições do repositório no momento de cada release.
- **Google ML Kit:** confirmar os termos de uso, atribuição, restrições de distribuição e dependências associadas à versão 16.0.1.

## Licença do código StudioMesa

Não foi encontrada uma licença global `LICENSE` na raiz do repositório. A ausência desse ficheiro não concede ao público uma licença de redistribuição do código. Antes de distribuir a app, o titular deve confirmar a autoria/cessão dos direitos do código original e escolher, com aconselhamento adequado, se o projeto será proprietário ou terá uma licença específica. Não adicionar uma licença de código aberto por conveniência sem autorização dos titulares.

## Tarefas obrigatórias antes de uma release comercial

- [ ] Gerar um SBOM (por exemplo, CycloneDX ou SPDX) dos artefactos Release e dos componentes nativos/transitivos de todas as cinco variantes.
- [ ] Recolher todas as licenças e NOTICE dos grafos reais de produção, não apenas das dependências declaradas diretamente.
- [ ] Rever ML Kit, desugar_jdk_libs, dependências JNI/C++, runtime LiteRT-LM e bibliotecas transitivas.
- [ ] Confirmar autorização comercial para a entidade responsável ao abrigo da LFM Open License v1.0 e cumprir atribuição/termos.
- [ ] Guardar as licenças e notices correspondentes à versão exata de cada AAB/APK assinado.
- [ ] Confirmar direitos sobre o nome/logótipo StudioMesa, ícones, imagens, fontes e restantes elementos gráficos.
- [ ] Definir e documentar a licença do código original e a titularidade dos direitos.
- [ ] Repetir a auditoria de dependências e vulnerabilidades na versão final.

**Não distribuir comercialmente até estes pontos estarem revistos e aprovados pelo titular legal.**