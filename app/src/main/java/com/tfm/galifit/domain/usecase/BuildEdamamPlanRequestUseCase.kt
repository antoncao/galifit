package com.tfm.galifit.domain.usecase

import com.tfm.galifit.data.model.EdamamPlanRequest
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.PreferencesToEdamamMapper

class BuildEdamamPlanRequestUseCase {

    operator fun invoke(prefs: UserPreferences?): EdamamPlanRequest =
        prefs?.let { PreferencesToEdamamMapper.buildRequest(it) }
            ?: PreferencesToEdamamMapper.defaultRequest()
}
