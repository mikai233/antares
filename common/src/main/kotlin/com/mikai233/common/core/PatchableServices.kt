package com.mikai233.common.core

import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.patch.PatchId
import io.github.realmlabs.asteria.patch.PatchOrder
import kotlin.reflect.KClass

private const val SCRIPT_PATCH_PRIORITY = 10_000
private const val SCRIPT_PATCH_SEQUENCE = 1L

fun NodeRuntime.scriptPatchOrder(id: String): PatchOrder {
    return PatchOrder(
        priority = SCRIPT_PATCH_PRIORITY,
        sequence = SCRIPT_PATCH_SEQUENCE,
        id = PatchId(id),
    )
}

fun <T : Any> NodeRuntime.replacePatchableService(
    type: KClass<T>,
    service: T,
    patchId: String,
) {
    patchableServices.replace(type, service, scriptPatchOrder(patchId))
}

inline fun <reified T : Any> NodeRuntime.replacePatchableService(
    service: T,
    patchId: String,
) {
    replacePatchableService(T::class, service, patchId)
}

fun NodeRuntime.removePatchedService(patchId: String) {
    patchableServices.remove(PatchId(patchId))
}
