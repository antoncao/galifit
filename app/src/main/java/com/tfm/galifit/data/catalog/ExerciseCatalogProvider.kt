package com.tfm.galifit.data.catalog

interface ExerciseCatalogProvider {

    suspend fun loadCatalog(forceRefresh: Boolean = false): ExerciseCatalog
}
