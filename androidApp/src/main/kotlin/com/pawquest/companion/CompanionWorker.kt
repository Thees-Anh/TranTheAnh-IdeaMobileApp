package com.pawquest.companion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pawquest.PawQuestApplication

class CompanionWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val coordinator=(applicationContext as PawQuestApplication).companion
        return if(coordinator.synchronize(inputData.getString("owner"))) Result.success() else Result.retry()
    }
}
