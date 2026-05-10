package com.mikai233.common.runtime.patch

import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import io.github.realmlabs.asteria.patch.PatchArtifactStore
import io.github.realmlabs.asteria.patch.WritablePatchArtifactStore

class GamePatchStoreModule(
    private val artifactStore: WritablePatchArtifactStore,
) : AsteriaModule {
    override val name: String = "game-patch-store"

    override suspend fun install(context: ModuleContext) {
        context.services.register(WritablePatchArtifactStore::class, artifactStore)
        context.services.register(PatchArtifactStore::class, artifactStore)
    }
}
