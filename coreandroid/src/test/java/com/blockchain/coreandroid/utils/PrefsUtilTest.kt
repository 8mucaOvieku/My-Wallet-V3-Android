package com.blockchain.coreandroid.utils

import android.content.Context
import android.content.SharedPreferences
import com.blockchain.core.utils.DeviceIdGeneratorService
import com.blockchain.core.utils.UUIDGenerator
import com.blockchain.enviroment.EnvironmentConfig
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.validateMockitoUsage
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetCatalogue
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class PrefsUtilTest {

    private val ctx: Context = mock()
    private val store: SharedPreferences = mock()
    private val backupStore: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val idGenerator: DeviceIdGeneratorService = mock()
    private val uuidGenerator: UUIDGenerator = mock()
    private val assetCatalogue: AssetCatalogue = mock()
    private val environmentConfig: EnvironmentConfig = mock()

    private val subject: PrefsUtil =
        PrefsUtil(ctx, store, backupStore, idGenerator, uuidGenerator, assetCatalogue, environmentConfig)

    @Before
    fun setUpSharedPrefs() {
        whenever(store.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
    }

    @Test
    fun getDeviceId_qaRandomiseNotSet_nothingStored() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(false)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn("")

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, STATIC_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator, never()).generateUUID()
    }

    @Test
    fun getDeviceId_qaRandomiseIsSet_nothingStored() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(uuidGenerator.generateUUID()).thenReturn(RANDOM_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(true)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn("")

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, RANDOM_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator).generateUUID()
    }

    @Test
    fun getDeviceId_qaRandomiseNotSet_returnStored() {
        // Arrange
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(false)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn(STATIC_DEVICE_ID)

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, STATIC_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator, never()).generateUUID()
        verify(idGenerator, never()).generateId()
    }

    @Test
    fun getDeviceId_qaRandomiseIsSet_valueStored_returnRandomised() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(uuidGenerator.generateUUID()).thenReturn(RANDOM_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(true)

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, RANDOM_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(store, never()).getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, "")
        verify(uuidGenerator).generateUUID()
    }

    companion object {
        private const val STATIC_DEVICE_ID = "12345678901234567890"
        private const val RANDOM_DEVICE_ID = "84962066204735275920"
    }

    @After
    fun validate() {
        validateMockitoUsage()
    }
}
