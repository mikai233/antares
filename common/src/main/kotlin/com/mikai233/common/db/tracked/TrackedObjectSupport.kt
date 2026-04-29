package com.mikai233.common.db.tracked

abstract class TrackedObjectSupport(
    private val queue: ChangeQueue,
) : PersistentValue, DirtyTargetAware {
    private var dirtyTarget: DirtyTarget? = null

    override fun bindDirtyTarget(dirtyTarget: DirtyTarget?) {
        this.dirtyTarget = dirtyTarget
    }

    protected fun markSet(path: DbPath, value: Any?) {
        queue.enqueueSet(path, value, dirtyTarget)
    }

    protected fun currentDirtyTarget(): DirtyTarget? {
        return dirtyTarget
    }
}

