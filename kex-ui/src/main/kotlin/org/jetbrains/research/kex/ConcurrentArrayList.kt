package org.jetbrains.research.kex

import kotlinx.coroutines.internal.SynchronizedObject

class ConcurrentArrayList<T> : ArrayList<T>() {
    private val lock = SynchronizedObject()
    override fun add(element: T): Boolean = synchronized(lock) {
        return super.add(element)
    }
}