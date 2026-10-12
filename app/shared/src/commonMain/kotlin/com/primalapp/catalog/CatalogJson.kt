package com.primalapp.catalog

import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.ForgeSlot
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ошибка формата каталога: файл по умолчанию или ответ сервера не разобран. */
class CatalogFormatException(message: String) : RuntimeException(message)

/**
 * Разбор каталога из JSON `GET /api/v1/catalog/bundle` (`web/doc/api.md` §4.1) — тот же формат лежит в assets
 * приложения как каталог по умолчанию. Эффекты — язык эффектов сервера: вид эффекта по имени ключа.
 */
object CatalogJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Catalog {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (exception: Exception) {
            throw CatalogFormatException("Каталог: неверный JSON (${exception.message})")
        }
        val version = root.string("version") ?: ""
        val bosses = root.array("bosses").map { boss(it.jsonObject) }
        val achievements = root.array("achievements").map {
            val node = it.jsonObject
            AchievementDef(node.requireString("code"), node.requireString("name"))
        }
        val quests = root.array("quests").map { node ->
            val quest = node.jsonObject
            val number = quest.requireInt("number")
            QuestDef(
                number = number,
                name = quest.requireString("name"),
                bossCode = quest.requireString("bossCode"),
                expansion = quest.string("expansion")?.let { expansion(it) },
                victory = effects(quest["victory"], "задание $number, победа"),
                expired = effects(quest["expired"], "задание $number, истечение")
            )
        }
        val chapters = root.array("chapters").map { node ->
            val chapter = node.jsonObject
            val number = chapter.requireInt("chapter")
            ChapterDef(number, effects(chapter["effects"], "глава $number"), decisions(chapter["decisions"], "глава $number"))
        }.sortedBy { it.chapter }
        val forge = root.array("forge").flatMap { board ->
            val boardObject = board.jsonObject
            val element = Element.parse(boardObject.string("element"))
                ?: throw CatalogFormatException("Кузня: неизвестная стихия ${boardObject.string("element")}")
            boardObject.array("items").map { forgeItem(it.jsonObject, element) }
        }
        val lab = root.array("lab").map { node ->
            val potion = node.jsonObject
            LabPotionDef(
                code = potion.requireString("code"),
                name = potion.requireString("name"),
                units = potion.array("units").map { unit ->
                    val unitObject = unit.jsonObject
                    LabUnit(
                        options = unitObject.array("options").mapNotNull { option -> plant(option.jsonPrimitive.contentOrNull) },
                        any = unitObject["any"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                }
            )
        }
        if (bosses.isEmpty() || quests.isEmpty() || chapters.isEmpty()) {
            throw CatalogFormatException("Каталог неполон: нет боссов, заданий или глав")
        }
        return Catalog(version, bosses, achievements, quests, chapters, forge, lab)
    }

    private fun boss(node: JsonObject): BossDef {
        val code = node.requireString("code")
        val difficulties = node["difficulties"]?.jsonObject ?: JsonObject(emptyMap())
        val stances = difficulties.entries.associate { (level, list) ->
            level.toInt() to list.jsonArray.map { stance ->
                val stanceObject = stance.jsonObject
                val change = stanceObject["stanceChange"]?.jsonObject
                StanceDef(
                    stance = stanceObject.requireInt("stance"),
                    toughnessPerHunter = stanceObject.int("toughnessPerHunter"),
                    changeMode = change?.string("mode")?.let { mode ->
                        StanceChangeMode.entries.firstOrNull { it.name == mode }
                    } ?: StanceChangeMode.FINAL,
                    changeAtHealth = change?.int("atHealth")
                )
            }
        }
        return BossDef(
            code = code,
            name = node.requireString("name"),
            element = Element.parse(node.string("element")),
            expansion = node.string("expansion")?.let { expansion(it) },
            sortOrder = node.int("sortOrder") ?: 0,
            stances = stances
        )
    }

    private fun forgeItem(node: JsonObject, element: Element): ForgeItemDef {
        val code = node.requireString("code")
        val slot = ForgeSlot.entries.firstOrNull { it.name == node.string("slot") }
            ?: throw CatalogFormatException("Кузня $code: неизвестное место ${node.string("slot")}")
        val costs = node.array("costs")
            .map { it.jsonObject }
            .sortedBy { it.int("level") ?: 0 }
            .map { cost ->
                (cost["materials"]?.jsonObject ?: JsonObject(emptyMap())).entries.mapNotNull { (key, value) ->
                    val material = Material.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
                    material to (value.jsonPrimitive.intOrNull ?: 0)
                }.toMap()
            }
        return ForgeItemDef(code, element, node.requireString("name"), slot, costs)
    }

    // region Язык эффектов

    fun effects(node: JsonElement?, where: String): List<Effect> {
        if (node == null || node is JsonNull) return emptyList()
        if (node !is JsonArray) throw CatalogFormatException("$where: ожидается список эффектов")
        return node.mapIndexed { index, effect -> effect(effect, "$where, эффект ${index + 1}") }
    }

    private fun effect(node: JsonElement, where: String): Effect {
        if (node !is JsonObject) throw CatalogFormatException("$where: эффект должен быть объектом")
        val expansion = node.string("expansion")?.let { expansion(it) }
        val keys = node.keys.filter { it != "expansion" }
        if ("if" in keys) {
            return Effect.Conditional(
                condition = condition(node["if"], "$where, if"),
                then = effects(node["then"], "$where, then"),
                otherwise = effects(node["else"], "$where, else"),
                expansion = expansion
            )
        }
        if (keys.size != 1) throw CatalogFormatException("$where: у эффекта должен быть ровно один вид, а указано $keys")
        val kind = keys.single()
        val value = node.getValue(kind)
        return when (kind) {
            "resources" -> Effect.Resources(resources(value, where), expansion)
            "openQuest" -> Effect.OpenQuest(integer(value, where), expansion)
            "expireQuests" -> Effect.ExpireQuests(integers(value, where), expansion)
            "expireAllQuests" -> Effect.ExpireAllQuests(expansion)
            "grantAchievement" -> Effect.GrantAchievement(text(value, where), expansion)
            "forgeLevelUp" -> Effect.ForgeLevelUp(expansion)
            "labLevelUp" -> Effect.LabLevelUp(expansion)
            "hunterKitUpgrade" -> Effect.HunterKitUpgrade(expansion)
            "rewardCards" -> Effect.RewardCards(texts(value, where), expansion)
            "message" -> Effect.Message(text(value, where), expansion)
            "finalBattle" -> Effect.FinalBattle(text(value, where), expansion)
            else -> throw CatalogFormatException("$where: неизвестный вид эффекта $kind")
        }
    }

    private fun condition(node: JsonElement?, where: String): Condition {
        if (node !is JsonObject || node.size != 1) throw CatalogFormatException("$where: условие — объект с одним ключом")
        val (kind, value) = node.entries.single()
        return when (kind) {
            "achievement" -> Condition.HasAchievement(text(value, where))
            "chapterIn" -> Condition.ChapterIn(integers(value, where))
            "questAvailable" -> Condition.QuestAvailable(integer(value, where))
            "expansion" -> Condition.HasExpansion(expansion(text(value, where)))
            "not" -> Condition.Not(condition(value, "$where, not"))
            "all" -> Condition.All(conditions(value, "$where, all"))
            "any" -> Condition.Any(conditions(value, "$where, any"))
            else -> throw CatalogFormatException("$where: неизвестное условие $kind")
        }
    }

    private fun conditions(node: JsonElement, where: String): List<Condition> {
        if (node !is JsonArray || node.isEmpty()) throw CatalogFormatException("$where: ожидается непустой список условий")
        return node.map { condition(it, where) }
    }

    private fun decisions(node: JsonElement?, where: String): List<Decision> {
        if (node == null || node is JsonNull) return emptyList()
        return node.jsonArray.map { decisionNode ->
            val decision = decisionNode.jsonObject
            val code = text(decision.getValue("code"), "$where, код решения")
            Decision(
                code = code,
                question = text(decision.getValue("question"), "$where, $code"),
                options = decision.array("options").map { optionNode ->
                    val option = optionNode.jsonObject
                    val optionCode = text(option.getValue("code"), "$where, $code")
                    DecisionOption(
                        code = optionCode,
                        label = text(option.getValue("label"), "$where, $code"),
                        effects = effects(option["effects"], "$where, $code.$optionCode")
                    )
                }
            )
        }
    }

    private fun resources(node: JsonElement, where: String): Map<ResourceCode, Int> {
        if (node !is JsonObject) throw CatalogFormatException("$where: ресурсы — объект")
        return node.entries.associate { (key, value) ->
            val code = ResourceCode.parse(key) ?: throw CatalogFormatException("$where: неизвестный ресурс $key")
            code to integer(value, where)
        }
    }

    private fun integer(node: JsonElement, where: String): Int =
        (node as? JsonPrimitive)?.intOrNull ?: throw CatalogFormatException("$where: ожидается целое число, а указано $node")

    private fun integers(node: JsonElement, where: String): List<Int> {
        if (node !is JsonArray) throw CatalogFormatException("$where: ожидается список чисел")
        return node.map { integer(it, where) }
    }

    private fun text(node: JsonElement, where: String): String {
        val value = (node as? JsonPrimitive)?.contentOrNull
        if (value.isNullOrBlank()) throw CatalogFormatException("$where: ожидается строка")
        return value
    }

    private fun texts(node: JsonElement, where: String): List<String> {
        if (node !is JsonArray) throw CatalogFormatException("$where: ожидается список строк")
        return node.map { text(it, where) }
    }

    // endregion

    private fun expansion(code: String): Expansion =
        Expansion.entries.firstOrNull { it.name == code } ?: throw CatalogFormatException("Неизвестное дополнение $code")

    private fun plant(code: String?): Plant? = Plant.entries.firstOrNull { it.name == code }

    private fun JsonObject.array(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.requireString(key: String): String = string(key) ?: throw CatalogFormatException("Нет поля $key")
    private fun JsonObject.requireInt(key: String): Int = int(key) ?: throw CatalogFormatException("Нет поля $key")
}
