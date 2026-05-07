package com.scoot.transit.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.domain.Agency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class GtfsRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val statics: GtfsStaticRepo,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operator = inputData.getString(KEY_OPERATOR) ?: return Result.failure()
        val agency = Agency.fromOperatorId(operator) ?: return Result.failure()
        return runCatching {
            statics.refresh(agency)
            Result.success()
        }.getOrElse {
            Timber.w(it, "GtfsRefreshWorker failed")
            Result.retry()
        }
    }

    companion object {
        private const val KEY_OPERATOR = "operator_id"
        fun input(agency: Agency): Data = Data.Builder().putString(KEY_OPERATOR, agency.operatorId).build()
    }
}
