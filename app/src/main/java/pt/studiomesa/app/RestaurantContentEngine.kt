package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

/**
 * Small, offline content engine for StudioMesa.
 *
 * This is a purpose-built rule/template engine, not a pretrained language model.
 * It never calls the network and only uses the title and facts supplied by the user.
 */
object RestaurantContentEngine {
    private val allowedTypes = setOf(
        "menu_description",
        "social_post",
        "campaign",
        "content_ideas"
    )

    private val emailPattern =
        Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
    private val portuguesePhonePattern =
        Regex("""(?<!\d)(?:\+351[\s().-]?)?(?:9[1236](?:[\s().-]?\d){7}|2(?:[\s().-]?\d){8})(?!\d)""")

    fun generate(contentType: String, title: String, facts: String): String {
        val cleanTitle = title.trim()
        val cleanFacts = facts.trim()

        require(contentType in allowedTypes) {
            "Escolhe um tipo de conteúdo válido."
        }
        require(cleanTitle.length in 2..120) {
            "O assunto deve ter entre 2 e 120 caracteres."
        }
        require(cleanFacts.length in 10..1500) {
            "Os factos devem ter entre 10 e 1500 caracteres."
        }
        require((cleanTitle + "\n" + cleanFacts).toByteArray(Charsets.UTF_8).size <= 4096) {
            "O texto introduzido é demasiado grande."
        }
        require(!containsCustomerContactData("$cleanTitle\n$cleanFacts")) {
            "Remove emails e números de telefone. Não são necessários dados pessoais para criar conteúdo."
        }

        val factSentence = endSentence(cleanFacts)
        val draft = when (contentType) {
            "menu_description" -> description(cleanTitle, factSentence)
            "social_post" -> socialPost(cleanTitle, factSentence)
            "campaign" -> campaign(cleanTitle, factSentence)
            "content_ideas" -> contentIdeas(cleanTitle, cleanFacts)
            else -> error("Tipo de conteúdo não suportado.")
        }
        require(draft.length <= 4000) { "O rascunho gerado excede o limite permitido." }
        return draft
    }

    fun containsCustomerContactData(value: String): Boolean =
        emailPattern.containsMatchIn(value) || portuguesePhonePattern.containsMatchIn(value)

    private fun description(title: String, facts: String): String {
        val opening = choose(title, listOf(
            "Conheça $title.",
            "Em destaque: $title.",
            "Uma apresentação de $title."
        ))
        val closing = choose(title.reversed(), listOf(
            "Descrição preparada com os elementos confirmados pelo restaurante.",
            "Uma apresentação clara, baseada na informação fornecida.",
            "Os detalhes apresentados são os indicados pelo restaurante."
        ))
        return "$opening\n\n$facts\n\n$closing"
    }

    private fun socialPost(title: String, facts: String): String {
        val normalized = normalize(title + " " + facts)
        val opening = when {
            listOf("sobremesa", "bolo", "tarte", "gelado").any(normalized::contains) ->
                choose(title, listOf("Para terminar, conheça $title.", "Um destaque para a sua próxima pausa: $title."))
            listOf("hamburguer", "bife", "carne", "grelhado").any(normalized::contains) ->
                choose(title, listOf("Em destaque: $title.", "Conheça melhor a proposta: $title."))
            listOf("peixe", "bacalhau", "salmao", "polvo", "marisco").any(normalized::contains) ->
                choose(title, listOf("Descubra os detalhes de $title.", "O destaque desta publicação é $title."))
            else -> choose(title, listOf("Conheça $title.", "Uma proposta a apresentar: $title.", "Em destaque na comunicação: $title."))
        }
        val callToAction = choose(title.reversed(), listOf(
            "Consulte a carta ou peça mais informações à equipa.",
            "Guarde esta sugestão e consulte os detalhes junto do restaurante.",
            "Partilhe a publicação com quem gostaria de conhecer esta proposta."
        ))
        return "$opening\n\n$facts\n\n$callToAction"
    }

    private fun campaign(title: String, facts: String): String {
        val opening = choose(title, listOf(
            "Destaque de comunicação: $title.",
            "Proposta para apresentar: $title.",
            "Tema da publicação: $title."
        ))
        val action = choose(title.reversed(), listOf(
            "Apresentar esta opção numa publicação e convidar o público a consultar a carta.",
            "Criar uma publicação clara com o nome e os detalhes confirmados desta opção.",
            "Dar visibilidade a esta proposta através de texto e imagem reais do restaurante."
        ))
        return "$opening\n\n$facts\n\nSugestão de campanha: $action"
    }

    private fun contentIdeas(title: String, facts: String): String =
        "1. Apresentação do artigo\n" +
            "Criar uma publicação com o nome «$title» e os factos confirmados: " + endSentence(facts) + "\n\n" +
            "2. Conteúdo visual\n" +
            "Fotografar ou filmar o artigo real, sem acrescentar ingredientes, elementos ou características que não tenham sido confirmados.\n\n" +
            "3. Pergunta à comunidade\n" +
            "Perguntar ao público que outros detalhes gostaria de conhecer sobre «$title»."

    private fun choose(seed: String, options: List<String>): String =
        options[Math.floorMod(seed.lowercase(Locale.ROOT).hashCode(), options.size)]

    private fun endSentence(value: String): String {
        val clean = value.trim()
        return if (clean.lastOrNull() in listOf('.', '!', '?', '…')) clean else "$clean."
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
}
