package com.mikai233.shared.excel


object ExcelConfigs {

    lateinit var fetchConfigManager: () -> GameConfigManager
        private set

    fun initialized() = this::fetchConfigManager.isInitialized

    inline fun <reified T : GameConfigs<*, *>> get(): T {
        return fetchConfigManager().get()
    }

    inline fun <reified T : GameConfigs<K, C>, C : GameConfig<K>, K : Any> getById(id: K): C {
        return fetchConfigManager().getById<T, _, _>(id)
    }
}