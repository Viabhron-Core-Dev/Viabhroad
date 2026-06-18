package com.example.keyboard

import kotlinx.coroutines.flow.Flow

class ClipboardRepository(private val clipboardDao: ClipboardDao) {

    val allItems: Flow<List<ClipboardItem>> = clipboardDao.getAllItemsFlow()

    fun insert(text: String) {
        val existing = clipboardDao.getItemByText(text)
        if (existing != null) {
            // Update timestamp to bubble it to the top
            clipboardDao.insert(existing.copy(timestamp = System.currentTimeMillis()))
        } else {
            clipboardDao.insert(ClipboardItem(text = text))
            trimUnpinned()
        }
    }

    fun togglePin(item: ClipboardItem) {
        clipboardDao.update(item.copy(isPinned = !item.isPinned))
    }

    fun delete(item: ClipboardItem) {
        clipboardDao.delete(item)
    }

    fun deleteAllUnpinned() {
        clipboardDao.deleteAllUnpinned()
    }

    private fun trimUnpinned() {
        val maxUnpinnedCount = 20
        val currentUnpinnedCount = clipboardDao.getUnpinnedCount()
        if (currentUnpinnedCount > maxUnpinnedCount) {
            val toDelete = currentUnpinnedCount - maxUnpinnedCount
            clipboardDao.deleteOldestUnpinned(toDelete)
        }
    }

    fun getAllItemsSync(): List<ClipboardItem> {
        return clipboardDao.getAllItems()
    }
}
