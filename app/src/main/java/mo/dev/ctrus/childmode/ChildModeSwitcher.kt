package mo.dev.ctrus.childmode

import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.PhysicalUnblockType

/**
 * Leaving Child Mode (spec Part B, 5.3; port of iOS's ChildModeSwitcher): everything that belongs
 * only to it goes; the profiles stay, and keep every Ctrus they were linked to (the parents', and
 * their own child's) as physical unlocks, so their sessions can still be ended in Normal Mode.
 */
object ChildModeSwitcher {
    suspend fun switchToNormal(app: CtrusApp, tagName: (Int) -> String) {
        val parentTags = ChildModeStore.parentTags

        ParentCredentials.clearAll()
        ChildModeStore.clearChildModeData()
        // Child Mode is off now, so this drops the session's automatic end.
        ChildSessionLimit.sync(app)

        app.profileRepository.getAll().forEach { profile ->
            app.profileRepository.update(
                profile.copy(
                    physicalUnblockItems = physicalUnblocks(profile, parentTags, tagName),
                    childTag = null,
                    childTagCanStop = false,
                ),
            )
        }
    }

    /**
     * The profile's own unlocks plus each linked Ctrus it doesn't have yet (compared by URL and
     * UID), stored the way Normal Mode reads a scanned tag: its URL, else its UID as uppercase hex.
     * Named "Ctrus N", after the ones already there.
     */
    fun physicalUnblocks(
        profile: BlockedProfileEntity,
        parentTags: List<ParentTag>,
        tagName: (Int) -> String,
    ): List<PhysicalUnblockItem> {
        val items = profile.physicalUnblockItems.toMutableList()
        val linked = parentTags.map { it.uid to it.url } + listOfNotNull(profile.childTag?.let { it.uid to it.url })

        for ((uid, url) in linked) {
            val codeValue = url ?: uid.uppercase()
            val alreadyThere = items.any {
                it.type == PhysicalUnblockType.NFC &&
                    (PhysicalUnblockItem.normalizedCodeValue(it.codeValue, it.type) == PhysicalUnblockItem.normalizedCodeValue(codeValue, it.type) ||
                        ChildModeStore.normalizedUid(it.codeValue) == ChildModeStore.normalizedUid(uid))
            }
            if (alreadyThere) continue
            val number = items.count { it.type == PhysicalUnblockType.NFC } + 1
            items += PhysicalUnblockItem(name = tagName(number), codeValue = codeValue)
        }
        return PhysicalUnblockItem.normalizedItems(items)
    }
}
