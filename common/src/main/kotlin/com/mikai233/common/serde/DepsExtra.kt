package com.mikai233.common.serde

import java.util.*
import java.util.concurrent.*
import kotlin.reflect.KClass

val DepsExtra = arrayOf<KClass<*>>(
    emptyList<Int>()::class,
    emptyMap<Int, Int>()::class,
    emptySet<Int>()::class,
    listOf(0)::class,
    mapOf(0 to 0)::class,
    setOf(0)::class,
    arrayOf(0)::class,
    Arrays.asList(0)::class,
    ArrayList::class,
    ByteArray::class,
    HashMap::class,
    HashSet::class,
    LinkedHashMap::class,
    LinkedHashSet::class,
    LinkedList::class,
    PriorityQueue::class,
    ArrayDeque::class,
    TreeMap::class,
    TreeSet::class,
    Hashtable::class,
    ConcurrentHashMap::class,
    WeakHashMap::class,
    IdentityHashMap::class,
    EnumSet::class,
    CopyOnWriteArrayList::class,
    CopyOnWriteArraySet::class,
    ArrayBlockingQueue::class,
    ConcurrentLinkedQueue::class,
    DelayQueue::class,
    LinkedBlockingQueue::class,
    PriorityBlockingQueue::class,
    SynchronousQueue::class,
    LinkedTransferQueue::class,
    ConcurrentSkipListMap::class,
    ConcurrentSkipListSet::class,
)