package com.primalapp.model.campaign

import com.primalapp.database.entity.BossEntity
import com.primalapp.database.mapper.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BossTest {

    //region Модель Boss — getStance

    @Test
    fun `Boss getStance возвращает стойку для валидного индекса`() {
        // Подготовка: босс с 3 стойками
        val boss = Boss(
            name = "Вираксен",
            element = Element.FIRE,
            difficulty = 0,
            stances = listOf(
                BossStance(2, 7),
                BossStance(3, 3),
                BossStance(4, 0)
            )
        )

        // Вызов проверяемого кода
        val stance = boss.getStance(1)

        // Проверка: вторая стойка (индекс 1) = damageForWound 3, healthForStanceChange 3
        assertEquals(3, stance?.damageForWound, "damageForWound второй стойки должен быть 3")
        assertEquals(3, stance?.healthForStanceChange, "healthForStanceChange второй стойки должен быть 3")
    }

    @Test
    fun `Boss getStance возвращает null для несуществующего индекса`() {
        // Подготовка: босс с 3 стойками
        val boss = Boss(
            name = "Вираксен",
            element = Element.FIRE,
            difficulty = 0,
            stances = listOf(BossStance(2, 7))
        )

        // Вызов проверяемого кода
        val stance = boss.getStance(5)

        // Проверка: null
        assertNull(stance, "getStance должен вернуть null для несуществующей стойки")
    }

    //endregion

    //region BossEntity.toDomain — маппинг стоек

    @Test
    fun `BossEntity toDomain маппит три стойки`() {
        // Подготовка: BossEntity с 3 стойками
        val entity = BossEntity(
            id = 1,
            name = "Вираксен",
            element = "FIRE",
            difficulty = 0,
            stance1Dfw = 2, stance1Hsc = 7,
            stance2Dfw = 3, stance2Hsc = 3,
            stance3Dfw = 4, stance3Hsc = 0
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: 3 стойки с корректными значениями
        assertEquals("Вираксен", boss.name)
        assertEquals(Element.FIRE, boss.element)
        assertEquals(0, boss.difficulty)
        assertEquals(3, boss.stances.size, "Должно быть 3 стойки")
        assertEquals(BossStance(2, 7), boss.stances[0])
        assertEquals(BossStance(3, 3), boss.stances[1])
        assertEquals(BossStance(4, 0), boss.stances[2])
    }

    @Test
    fun `BossEntity toDomain маппит пять стоек`() {
        // Подготовка: BossEntity с 5 стойками
        val entity = BossEntity(
            id = 2,
            name = "Босс5",
            element = "ICE",
            difficulty = 2,
            stance1Dfw = 1, stance1Hsc = 1,
            stance2Dfw = 2, stance2Hsc = 2,
            stance3Dfw = 3, stance3Hsc = 3,
            stance4Dfw = 4, stance4Hsc = 4,
            stance5Dfw = 5, stance5Hsc = 5
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: 5 стоек
        assertEquals(5, boss.stances.size, "Должно быть 5 стоек")
        assertEquals(BossStance(5, 5), boss.stances[4], "Пятая стойка должна быть (5, 5)")
    }

    @Test
    fun `BossEntity toDomain пропускает нулевые стойки`() {
        // Подготовка: BossEntity с 3 стойками, 4-я и 5-я = 0
        val entity = BossEntity(
            id = 3,
            name = "Вираксен",
            element = "FIRE",
            difficulty = 1,
            stance1Dfw = 5, stance1Hsc = 7,
            stance2Dfw = 7, stance2Hsc = 3,
            stance3Dfw = 10, stance3Hsc = 0,
            stance4Dfw = 0, stance4Hsc = 0,
            stance5Dfw = 0, stance5Hsc = 0
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: только 3 стойки (нулевые пропущены)
        assertEquals(3, boss.stances.size, "Нулевые стойки должны быть пропущены")
    }

    //endregion

    //region 15.x. Null healthForStanceChange (Иекорос)

    @Test
    fun `BossStance допускает null healthForStanceChange`() {
        // Подготовка: стойка с null hsc (смена по запросу)

        // Вызов проверяемого кода
        val stance = BossStance(damageForWound = 2, healthForStanceChange = null)

        // Проверка
        assertEquals(2, stance.damageForWound, "damageForWound должен быть 2")
        assertNull(stance.healthForStanceChange,
            "healthForStanceChange должен быть null (смена по запросу)")
    }

    @Test
    fun `BossEntity toDomain маппит null hsc для Иекороса`() {
        // Подготовка: BossEntity Иекороса с null hsc во всех стойках
        val entity = BossEntity(
            id = 5,
            name = "Иекорос",
            element = "LIGHTNING",
            difficulty = 0,
            stance1Dfw = 2, stance1Hsc = null,
            stance2Dfw = 4, stance2Hsc = null,
            stance3Dfw = 5, stance3Hsc = null
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: 3 стойки с null hsc
        assertEquals("Иекорос", boss.name)
        assertEquals(Element.LIGHTNING, boss.element)
        assertEquals(3, boss.stances.size)
        assertEquals(2, boss.stances[0].damageForWound)
        assertNull(boss.stances[0].healthForStanceChange,
            "hsc первой стойки Иекороса должен быть null")
        assertEquals(5, boss.stances[2].damageForWound)
        assertNull(boss.stances[2].healthForStanceChange)
    }

    //endregion

    //region 18.x. Nullable element (Пробуждённый)

    @Test
    fun `Boss допускает element null`() {
        // Подготовка: босс без стихии (Пробуждённый)

        // Вызов проверяемого кода
        val boss = Boss(
            name = "Пробуждённый",
            element = null,
            difficulty = 3,
            stances = listOf(BossStance(30, 8))
        )

        // Проверка
        assertEquals("Пробуждённый", boss.name)
        assertNull(boss.element, "element должен быть null для Пробуждённого")
        assertEquals(3, boss.difficulty)
    }

    @Test
    fun `BossEntity toDomain маппит null element`() {
        // Подготовка: BossEntity Пробуждённого с element = null
        val entity = BossEntity(
            id = 9,
            name = "Пробуждённый",
            element = null,
            difficulty = 3,
            stance1Dfw = 30, stance1Hsc = 8,
            stance2Dfw = 40, stance2Hsc = 6,
            stance3Dfw = 50, stance3Hsc = 4,
            stance4Dfw = 60, stance4Hsc = 2,
            stance5Dfw = 60, stance5Hsc = 0
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: 5 стоек, element = null
        assertEquals("Пробуждённый", boss.name)
        assertNull(boss.element, "element должен быть null")
        assertEquals(5, boss.stances.size, "Должно быть 5 стоек")
        assertEquals(30, boss.stances[0].damageForWound)
        assertEquals(8, boss.stances[0].healthForStanceChange)
        assertEquals(0, boss.stances[4].healthForStanceChange)
    }

    //endregion

    //region 21.3. Seed 6 новых боссов (Торамат, Юром, Озев, Моркраас, Дигоракс, Харджа)

    @Test
    fun `BossEntity toDomain маппит все сложности Торамата`() {
        // Подготовка: ожидаемые стойки для всех сложностей Торамата (Рог)
        val expected = mapOf(
            0 to listOf(BossStance(2, 7), BossStance(3, 4), BossStance(3, 0)),
            1 to listOf(BossStance(4, 7), BossStance(6, 4), BossStance(9, 0)),
            2 to listOf(BossStance(10, 7), BossStance(16, 4), BossStance(20, 0)),
            3 to listOf(BossStance(18, 7), BossStance(25, 4), BossStance(30, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Торамат",
                element = "HORN",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Торамат", boss.name, "Имя должно быть Торамат")
            assertEquals(Element.HORN, boss.element, "Стихия должна быть HORN (Рог)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Юрома`() {
        // Подготовка: ожидаемые стойки для всех сложностей Юрома (Металл)
        val expected = mapOf(
            0 to listOf(BossStance(2, 6), BossStance(2, 3), BossStance(3, 0)),
            1 to listOf(BossStance(4, 6), BossStance(6, 3), BossStance(7, 0)),
            2 to listOf(BossStance(9, 6), BossStance(14, 3), BossStance(17, 0)),
            3 to listOf(BossStance(15, 7), BossStance(20, 3), BossStance(25, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Юром",
                element = "METAL",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Юром", boss.name, "Имя должно быть Юром")
            assertEquals(Element.METAL, boss.element, "Стихия должна быть METAL (Металл)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Озева`() {
        // Подготовка: ожидаемые стойки для всех сложностей Озева (Молния)
        val expected = mapOf(
            0 to listOf(BossStance(3, 8), BossStance(2, 3), BossStance(2, 0)),
            1 to listOf(BossStance(7, 7), BossStance(5, 3), BossStance(3, 0)),
            2 to listOf(BossStance(16, 7), BossStance(12, 3), BossStance(9, 0)),
            3 to listOf(BossStance(25, 7), BossStance(18, 3), BossStance(15, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Озев",
                element = "LIGHTNING",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Озев", boss.name, "Имя должно быть Озев")
            assertEquals(Element.LIGHTNING, boss.element, "Стихия должна быть LIGHTNING (Молния)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Моркрааса`() {
        // Подготовка: ожидаемые стойки для всех сложностей Моркрааса (Кристалл)
        val expected = mapOf(
            0 to listOf(BossStance(5, 6), BossStance(3, 3), BossStance(2, 0)),
            1 to listOf(BossStance(10, 6), BossStance(8, 3), BossStance(6, 0)),
            2 to listOf(BossStance(20, 6), BossStance(16, 3), BossStance(14, 0)),
            3 to listOf(BossStance(30, 6), BossStance(25, 6), BossStance(20, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Моркраас",
                element = "CRYSTAL",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Моркраас", boss.name, "Имя должно быть Моркраас")
            assertEquals(Element.CRYSTAL, boss.element, "Стихия должна быть CRYSTAL (Кристалл)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Дигоракса`() {
        // Подготовка: ожидаемые стойки для всех сложностей Дигоракса (Рог)
        val expected = mapOf(
            0 to listOf(BossStance(2, 8), BossStance(3, 4), BossStance(4, 0)),
            1 to listOf(BossStance(5, 8), BossStance(7, 4), BossStance(10, 0)),
            2 to listOf(BossStance(9, 8), BossStance(14, 4), BossStance(18, 0)),
            3 to listOf(BossStance(15, 8), BossStance(20, 4), BossStance(25, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Дигоракс",
                element = "HORN",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Дигоракс", boss.name, "Имя должно быть Дигоракс")
            assertEquals(Element.HORN, boss.element, "Стихия должна быть HORN (Рог)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Харджи`() {
        // Подготовка: ожидаемые стойки для всех сложностей Харджи (Огонь)
        val expected = mapOf(
            0 to listOf(BossStance(2, 6), BossStance(3, 2), BossStance(5, 0)),
            1 to listOf(BossStance(5, 6), BossStance(7, 2), BossStance(12, 0)),
            2 to listOf(BossStance(10, 7), BossStance(16, 2), BossStance(20, 0)),
            3 to listOf(BossStance(15, 7), BossStance(25, 2), BossStance(30, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Харджа",
                element = "FIRE",
                difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Харджа", boss.name, "Имя должно быть Харджа")
            assertEquals(Element.FIRE, boss.element, "Стихия должна быть FIRE (Огонь)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `Boss getStance для новых боссов возвращает стойки 0-2 и null для индекса 3`() {
        // Подготовка: босс Торамат с 3 стойками
        val entity = BossEntity(
            name = "Торамат", element = "HORN", difficulty = 0,
            stance1Dfw = 2, stance1Hsc = 7,
            stance2Dfw = 3, stance2Hsc = 4,
            stance3Dfw = 3, stance3Hsc = 0
        )
        val boss = entity.toDomain()

        // Вызов проверяемого кода
        val stance0 = boss.getStance(0)
        val stance1 = boss.getStance(1)
        val stance2 = boss.getStance(2)
        val stance3 = boss.getStance(3)

        // Проверка: стойки 0–2 возвращаются, индекс 3 → null
        assertEquals(BossStance(2, 7), stance0, "Стойка 0 должна быть (2, 7)")
        assertEquals(BossStance(3, 4), stance1, "Стойка 1 должна быть (3, 4)")
        assertEquals(BossStance(3, 0), stance2, "Стойка 2 должна быть (3, 0)")
        assertNull(stance3, "Для босса с 3 стойками индекс 3 должен вернуть null")
    }

    //endregion

    //region 22.5. Коровон — стойка без порога раны (nullable dfw)

    @Test
    fun `BossStance допускает null damageForWound`() {
        // Подготовка: стойка без порога раны

        // Вызов проверяемого кода
        val stance = BossStance(damageForWound = null, healthForStanceChange = null)

        // Проверка
        assertNull(stance.damageForWound, "damageForWound должен быть null")
        assertNull(stance.healthForStanceChange, "healthForStanceChange должен быть null")
    }

    @Test
    fun `BossEntity toDomain маппит Коровона со стойкой без порога раны`() {
        // Подготовка: BossEntity Коровона (вторая стойка — dfw/hsc null)
        val entity = BossEntity(
            name = "Коровон",
            element = "CORAL",
            difficulty = 0,
            stance1Dfw = 2, stance1Hsc = 6,
            stance2Dfw = null, stance2Hsc = null,
            stance3Dfw = 4, stance3Hsc = 0
        )

        // Вызов проверяемого кода
        val boss = entity.toDomain()

        // Проверка: 3 стойки, вторая — без порога раны
        assertEquals("Коровон", boss.name)
        assertEquals(Element.CORAL, boss.element, "Стихия должна быть CORAL (Коралл)")
        assertEquals(3, boss.stances.size, "Должно быть 3 стойки")
        assertEquals(BossStance(2, 6), boss.stances[0])
        assertEquals(BossStance(null, null), boss.stances[1], "Вторая стойка должна быть без порога раны")
        assertEquals(BossStance(4, 0), boss.stances[2])
    }

    @Test
    fun `Boss getStance для Коровона возвращает null dfw на индексе 1 и null на индексе 3`() {
        // Подготовка: босс Коровон с 3 стойками (вторая без порога раны)
        val boss = Boss(
            name = "Коровон",
            element = Element.CORAL,
            difficulty = 0,
            stances = listOf(BossStance(2, 6), BossStance(null, null), BossStance(4, 0))
        )

        // Вызов проверяемого кода
        val stance1 = boss.getStance(1)
        val stance3 = boss.getStance(3)

        // Проверка
        assertEquals(BossStance(null, null), stance1, "Вторая стойка должна быть без порога раны")
        assertNull(stance3, "Для босса с 3 стойками индекс 3 должен вернуть null")
    }

    //endregion

    //region 23.3. Seed 9 новых боссов (Таррагуа, Фелаксир, Оруксен, Пазис, Нагарджас, Зекалит, Зекат, Тараск, Кситерос)

    @Test
    fun `BossEntity toDomain маппит все сложности Таррагуа`() {
        // Подготовка: ожидаемые стойки Таррагуа (Металл)
        val expected = mapOf(
            0 to listOf(BossStance(2, 6), BossStance(3, 3), BossStance(4, 0)),
            1 to listOf(BossStance(6, 6), BossStance(7, 3), BossStance(8, 0)),
            2 to listOf(BossStance(10, 6), BossStance(14, 3), BossStance(18, 0)),
            3 to listOf(BossStance(16, 6), BossStance(18, 3), BossStance(22, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Таррагуа", element = "METAL", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Таррагуа", boss.name, "Имя должно быть Таррагуа")
            assertEquals(Element.METAL, boss.element, "Стихия должна быть METAL (Металл)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Фелаксира`() {
        // Подготовка: ожидаемые стойки Фелаксира (Кристалл)
        val expected = mapOf(
            0 to listOf(BossStance(2, 7), BossStance(3, 3), BossStance(4, 0)),
            1 to listOf(BossStance(6, 7), BossStance(7, 3), BossStance(9, 0)),
            2 to listOf(BossStance(12, 7), BossStance(14, 4), BossStance(20, 0)),
            3 to listOf(BossStance(18, 7), BossStance(25, 4), BossStance(28, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Фелаксир", element = "CRYSTAL", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Фелаксир", boss.name, "Имя должно быть Фелаксир")
            assertEquals(Element.CRYSTAL, boss.element, "Стихия должна быть CRYSTAL (Кристалл)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Оруксена`() {
        // Подготовка: ожидаемые стойки Оруксена (Коралл)
        val expected = mapOf(
            0 to listOf(BossStance(2, 6), BossStance(3, 3), BossStance(4, 0)),
            1 to listOf(BossStance(6, 6), BossStance(7, 3), BossStance(8, 0)),
            2 to listOf(BossStance(10, 6), BossStance(15, 3), BossStance(20, 0)),
            3 to listOf(BossStance(20, 6), BossStance(22, 3), BossStance(26, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Оруксен", element = "CORAL", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Оруксен", boss.name, "Имя должно быть Оруксен")
            assertEquals(Element.CORAL, boss.element, "Стихия должна быть CORAL (Коралл)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Пазиса`() {
        // Подготовка: ожидаемые стойки Пазиса (Перо)
        val expected = mapOf(
            0 to listOf(BossStance(2, 8), BossStance(2, 5), BossStance(3, 0)),
            1 to listOf(BossStance(4, 7), BossStance(5, 4), BossStance(7, 0)),
            2 to listOf(BossStance(10, 8), BossStance(13, 5), BossStance(17, 0)),
            3 to listOf(BossStance(18, 8), BossStance(20, 5), BossStance(25, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Пазис", element = "FEATHER", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Пазис", boss.name, "Имя должно быть Пазис")
            assertEquals(Element.FEATHER, boss.element, "Стихия должна быть FEATHER (Перо)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Нагарджаса`() {
        // Подготовка: ожидаемые стойки Нагарджаса (Перо)
        val expected = mapOf(
            0 to listOf(BossStance(4, 7), BossStance(4, 4), BossStance(5, 0)),
            1 to listOf(BossStance(5, 7), BossStance(6, 4), BossStance(7, 0)),
            2 to listOf(BossStance(11, 7), BossStance(15, 4), BossStance(16, 0)),
            3 to listOf(BossStance(18, 7), BossStance(22, 4), BossStance(28, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Нагарджас", element = "FEATHER", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Нагарджас", boss.name, "Имя должно быть Нагарджас")
            assertEquals(Element.FEATHER, boss.element, "Стихия должна быть FEATHER (Перо)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит все сложности Тараска`() {
        // Подготовка: ожидаемые стойки Тараска (Огонь)
        val expected = mapOf(
            0 to listOf(BossStance(5, 8), BossStance(3, 4), BossStance(3, 0)),
            1 to listOf(BossStance(9, 8), BossStance(8, 4), BossStance(7, 0)),
            2 to listOf(BossStance(20, 8), BossStance(16, 4), BossStance(14, 0)),
            3 to listOf(BossStance(28, 8), BossStance(25, 4), BossStance(22, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Тараск", element = "FIRE", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Тараск", boss.name, "Имя должно быть Тараск")
            assertEquals(Element.FIRE, boss.element, "Стихия должна быть FIRE (Огонь)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит Кситероса со сменой по запросу на Ст2`() {
        // Подготовка: ожидаемые стойки Кситероса (Перо), Ст2 — hsc null
        val expected = mapOf(
            0 to listOf(BossStance(3, 7), BossStance(4, null), BossStance(5, 0)),
            1 to listOf(BossStance(7, 7), BossStance(8, null), BossStance(12, 0)),
            2 to listOf(BossStance(15, 7), BossStance(20, null), BossStance(25, 0)),
            3 to listOf(BossStance(20, 7), BossStance(25, null), BossStance(35, 0))
        )

        expected.forEach { (difficulty, stances) ->
            val entity = BossEntity(
                name = "Кситерос", element = "FEATHER", difficulty = difficulty,
                stance1Dfw = stances[0].damageForWound, stance1Hsc = stances[0].healthForStanceChange,
                stance2Dfw = stances[1].damageForWound, stance2Hsc = stances[1].healthForStanceChange,
                stance3Dfw = stances[2].damageForWound, stance3Hsc = stances[2].healthForStanceChange
            )

            // Вызов проверяемого кода
            val boss = entity.toDomain()

            // Проверка
            assertEquals("Кситерос", boss.name, "Имя должно быть Кситерос")
            assertEquals(Element.FEATHER, boss.element, "Стихия должна быть FEATHER (Перо)")
            assertEquals(difficulty, boss.difficulty, "Сложность должна совпадать")
            assertEquals(stances, boss.stances, "Стойки сложности $difficulty должны совпадать")
        }
    }

    @Test
    fun `BossEntity toDomain маппит Зеката и Зекалита одинаково`() {
        // Подготовка: ожидаемые стойки (одинаковые для обоих)
        val expected = listOf(BossStance(2, 6), BossStance(3, 2), BossStance(3, 0))
        val zekalit = BossEntity(
            name = "Зекалит", element = "LIGHTNING", difficulty = 0,
            stance1Dfw = 2, stance1Hsc = 6, stance2Dfw = 3, stance2Hsc = 2, stance3Dfw = 3, stance3Hsc = 0
        )
        val zekat = BossEntity(
            name = "Зекат", element = "LIGHTNING", difficulty = 0,
            stance1Dfw = 2, stance1Hsc = 6, stance2Dfw = 3, stance2Hsc = 2, stance3Dfw = 3, stance3Hsc = 0
        )

        // Вызов проверяемого кода
        val zekalitBoss = zekalit.toDomain()
        val zekatBoss = zekat.toDomain()

        // Проверка: стойки идентичны
        assertEquals("Зекалит", zekalitBoss.name, "Имя должно быть Зекалит")
        assertEquals("Зекат", zekatBoss.name, "Имя должно быть Зекат")
        assertEquals(Element.LIGHTNING, zekalitBoss.element, "Стихия Зекалита должна быть LIGHTNING")
        assertEquals(Element.LIGHTNING, zekatBoss.element, "Стихия Зеката должна быть LIGHTNING")
        assertEquals(expected, zekalitBoss.stances, "Стойки Зекалита должны совпадать с ожидаемыми")
        assertEquals(zekalitBoss.stances, zekatBoss.stances, "Стойки Зеката и Зекалита должны быть идентичны")
    }

    //endregion
}
