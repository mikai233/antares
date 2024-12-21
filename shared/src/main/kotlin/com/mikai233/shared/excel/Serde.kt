package com.mikai233.shared.excel

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.reflect.full.primaryConstructor
import kotlin.system.measureTimeMillis

val GAME_CONFIG_KRYO_POOL = KryoPool(CONFIG_DEPS + CONFIG_IMPL + CONFIGS_IMPL + DEPS_EXTRA)

/**
 * 由于Kryo序列化的一些限制，不能把[GameConfigManager]整个对象进行序列化和反序列化，而是需要把每个[GameConfigs]进行序列化和反序列化
 * 最后再组装数据
 * 因为[GameConfigManager.configs]和[GameConfigs.configs]的类型是[K]和[V]，[K]和[V]中含有通配符，在反射注册类型时，无法获取具体的类型，
 * 需要把序列化和反序列化延迟到具体的实现类中，所以在[GameConfigs]中添加了[GameConfigs.serialize]和[GameConfigs.deserialize]方法
 */
object ConfigManagerSerializer {

    fun deserializeInputStream(fis: InputStream): GameConfigManager =
        Input(GZIPInputStream(fis)).use { deserialize(it) }

    fun deserializeBytes(bytes: ByteArray): GameConfigManager =
        Input(GZIPInputStream(bytes.inputStream())).use { deserialize(it) }

    fun serializeToFile(manager: GameConfigManager, fos: FileOutputStream) =
        Output(GZIPOutputStream(fos)).use { serialize(manager, it) }

    fun serializeToBytes(manager: GameConfigManager): ByteArray {
        val bos = ByteArrayOutputStream()
        Output(GZIPOutputStream(bos)).use { serialize(manager, it) }
        return bos.toByteArray()
    }

    private fun deserialize(input: Input): GameConfigManager {
        return GAME_CONFIG_KRYO_POOL.use {
            val manager = readObject(input, GameConfigManager::class.java)
            val size = readObject(input, Int::class.java)
            repeat(size) {
                @Suppress("UNCHECKED_CAST")
                val key = readClass(input).type.kotlin as K
                val constructor =
                    requireNotNull(key.primaryConstructor) { "${key.simpleName} primaryConstructor is null" }
                val gameConfigs = constructor.call()
                gameConfigs.manager = manager
                gameConfigs.deserialize(this, input)
                manager.configs[key] = gameConfigs
            }
            manager
        }
    }

    private fun serialize(manager: GameConfigManager, output: Output) {
        GAME_CONFIG_KRYO_POOL.use {
            writeObject(output, manager)
            writeObject(output, manager.configs.size)
            manager.configs.values.forEach {
                writeClass(output, it::class.java)
                it.serialize(this, output)
            }
        }
    }
}

suspend fun createGameConfigManager(excelDir: String, version: String): GameConfigManager {
    val manager = GameConfigManager(version)
    val costMillis = measureTimeMillis {
        manager.load(excelDir)
    }
    manager.logger.info("Read excel cost $costMillis ms")
    return manager
}

suspend fun main() {
    val manager = createGameConfigManager(
        "F:\\MiscProjects\\design\\OutPut\\cn\\ExportSheet",
        "0.1.0",
    )
    withContext(Dispatchers.IO) {
        ConfigManagerSerializer.serializeToFile(
            manager,
            FileOutputStream("excel_bin.tar.gz")
        )
    }
    val bytes = ConfigManagerSerializer.serializeToBytes(manager)
    val manager2 = ConfigManagerSerializer.deserializeBytes(bytes)
    manager2.loadComplete()
}