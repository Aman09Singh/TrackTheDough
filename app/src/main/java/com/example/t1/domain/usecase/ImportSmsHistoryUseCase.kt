package com.example.t1.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.t1.data.sms.SmsImporter
import javax.inject.Inject

class ImportSmsHistoryUseCase @Inject constructor(
    private val smsImporter: SmsImporter,
    private val dataStore: DataStore<Preferences>,
) {
    suspend operator fun invoke() {
        smsImporter.importAll()
        dataStore.edit { it[SMS_IMPORTED_KEY] = true }
    }

    companion object {
        val SMS_IMPORTED_KEY = booleanPreferencesKey("sms_imported")
    }
}
