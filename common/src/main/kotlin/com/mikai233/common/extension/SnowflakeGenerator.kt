import java.util.concurrent.atomic.AtomicLong

/**
 * 0 | 41-bit 时间戳 | 10-bit 机器ID | 12-bit 序列号
 * - 41-bit 时间戳：表示从 epoch 开始的毫秒数。
 * - 10-bit 机器 ID：支持 1000 台机器。
 * - 12-bit 序列号：每毫秒支持 4096 个 ID。
 */
class SnowflakeGenerator(
    private val workerId: Long,
    private val epoch: Long = 1735660800000L // 自定义起始时间戳 (默认是 2025-01-01 00:00:00)
) {
    private val sequenceBits = 12
    private val workerIdBits = 10 // 支持 1024 台机器

    private val maxWorkerId = (1L shl workerIdBits) - 1
    private val sequenceMask = (1L shl sequenceBits) - 1

    private val workerIdShift = sequenceBits
    private val timestampShift = sequenceBits + workerIdBits

    private val lastTimestamp = AtomicLong(-1L)
    private val sequence = AtomicLong(0L)

    init {
        require(workerId in 0..maxWorkerId) { "Worker ID must be in range [0, $maxWorkerId]" }
    }

    fun nextId(): Long {
        while (true) {
            val currentTimestamp = currentTimeMillis()
            val lastTs = lastTimestamp.get()

            if (currentTimestamp < lastTs) {
                throw IllegalStateException("Clock moved backwards. Refusing to generate ID")
            }

            if (currentTimestamp == lastTs) {
                // 同一毫秒内递增序列号
                val currentSequence = (sequence.incrementAndGet() and sequenceMask)
                if (currentSequence == 0L) {
                    // 序列号溢出，等待下一毫秒
                    val nextTimestamp = waitNextMillis(currentTimestamp)
                    if (lastTimestamp.compareAndSet(lastTs, nextTimestamp)) {
                        sequence.set(0L) // 重置序列号
                    }
                    continue
                }

                // 尝试生成 ID
                val id = buildId(currentTimestamp, currentSequence)
                if (lastTimestamp.compareAndSet(lastTs, currentTimestamp)) {
                    return id
                }
            } else {
                // 新毫秒，重置序列号并生成 ID
                sequence.set(0L)
                if (lastTimestamp.compareAndSet(lastTs, currentTimestamp)) {
                    return buildId(currentTimestamp, sequence.get())
                }
            }
        }
    }

    private fun buildId(timestamp: Long, sequence: Long): Long {
        return ((timestamp - epoch) shl timestampShift) or
                (workerId shl workerIdShift) or
                sequence
    }

    private fun waitNextMillis(currentTimestamp: Long): Long {
        var timestamp = currentTimeMillis()
        while (timestamp <= currentTimestamp) {
            timestamp = currentTimeMillis()
        }
        return timestamp
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()
}