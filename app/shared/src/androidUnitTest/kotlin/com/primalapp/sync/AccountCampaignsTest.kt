package com.primalapp.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.campaign.CampaignState
import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.database.entity.CampaignStoreEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Кампании и вход: выход из аккаунта не убирает кампании с устройства, доступы гостя переходят к аккаунту. */
@RunWith(RobolectricTestRunner::class)
class AccountCampaignsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val core = AppCore(PlatformContext(context), db, CoroutineScope(Dispatchers.Unconfined))

    private fun copy(account: String, serverId: Long, ops: String = "[]") = CampaignStoreEntity(
        serverId = serverId,
        account = account,
        access = CampaignAccess.OWNER.name,
        stateJson = core.store.encodeState(CampaignState(name = "Кампания $serverId")),
        pendingOps = ops,
        createdAt = 1,
        updatedAt = 1
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun `после выхода из аккаунта кампании остаются на устройстве`() = runBlocking {
        // Подготовка: вход выполнен, на устройстве копия кампании аккаунта
        core.settings.setServer("http://127.0.0.1:9")
        core.settings.setAccount(AccountInfo("http://127.0.0.1:9", "USER", 7, "alice", "Алиса", "d1"))
        val localId = core.store.insertEntity(copy("http://127.0.0.1:9|u7", 12))

        // Вызов проверяемого кода: сервер недоступен — выход всё равно выполняется
        core.logout()

        // Проверка
        assertNull(core.settings.current.account)
        assertEquals(listOf(localId), core.store.all().map { it.localId })
        assertEquals("http://127.0.0.1:9|u7", core.store.get(localId)!!.account, "Копия остаётся за аккаунтом — до повторного входа")
    }

    @Test
    fun `копии гостя по ссылке переходят к аккаунту, копии другого аккаунта не трогаются`() = runBlocking {
        // Подготовка
        val guest = core.store.insertEntity(copy("srv|dguest", 12))
        val other = core.store.insertEntity(copy("srv|u8", 13))

        // Вызов проверяемого кода
        core.store.reassign("srv|dguest", "srv|u7")

        // Проверка
        assertEquals("srv|u7", core.store.get(guest)!!.account)
        assertEquals("srv|u8", core.store.get(other)!!.account)
    }
}
