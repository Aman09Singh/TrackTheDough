package com.example.t1.domain.usecase

import com.example.t1.data.sms.SmsImporter
import javax.inject.Inject

class ImportSmsHistoryUseCase @Inject constructor(
    private val smsImporter: SmsImporter,
) {
    // Always runs — SmsImporter deduplicates via smsId so re-running is safe.
    // Returns false only if SMS permission was not granted.
    suspend operator fun invoke(): Boolean = smsImporter.importAll()
}
