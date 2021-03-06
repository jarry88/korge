package com.soywiz.korge.animate

import com.soywiz.kds.*
import com.soywiz.kds.iterators.*
import com.soywiz.klock.*
import com.soywiz.korge.internal.*
import com.soywiz.korge.tween.*
import com.soywiz.korge.view.*
import com.soywiz.korio.async.*
import com.soywiz.korma.geom.Angle
import com.soywiz.korma.geom.plus
import com.soywiz.korma.interpolation.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.math.*

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class AnimatorDslMarker

interface BaseAnimatorNode {
    suspend fun execute()
    fun executeImmediately()
}

open class Animator(
    val root: View,
    val time: TimeSpan = DEFAULT_TIME,
    val speed: Double = DEFAULT_SPEED,
    val easing: Easing = DEFAULT_EASING,
    val completeOnCancel: Boolean = DEFAULT_COMPLETE_ON_CANCEL,
    val kind: NodeKind = NodeKind.Sequence,
    val looped: Boolean = false,
    val init: Animator.() -> Unit = {}
) : BaseAnimatorNode {
    companion object {
        val DEFAULT_TIME = 500.milliseconds
        val DEFAULT_SPEED = 128.0 // Points per second
        val DEFAULT_EASING = Easing.EASE_IN_OUT_QUAD
        val DEFAULT_COMPLETE_ON_CANCEL = true
    }

    enum class NodeKind {
        Parallel, Sequence
    }

    private var initialized = false
    private var _onCancel: () -> Unit = {}

    fun onCancel(action: () -> Unit) {
        _onCancel = action
    }

    @PublishedApi
    internal val nodes = Deque<BaseAnimatorNode>()

    override suspend fun execute() {
        if (!initialized) {
            init(this)
            initialized = true
        }
        when (kind) {
            NodeKind.Sequence -> {
                try {
                    executeLoopedOrNot {
                        while (nodes.isNotEmpty()) nodes.removeFirst().execute()
                    }
                } catch (e: CancellationException) {
                    _onCancel()
                    if (completeOnCancel) {
                        while (nodes.isNotEmpty()) nodes.removeFirst().executeImmediately()
                    }
                }
            }
            NodeKind.Parallel -> {
                try {
                    executeLoopedOrNot {
                        val jobs = arrayListOf<Job>()
                        while (nodes.isNotEmpty()) {
                            val node = nodes.removeFirst()
                            jobs += launchImmediately(coroutineContext) { node.execute() }
                        }
                        jobs.joinAll()
                    }
                } catch (e: CancellationException) {
                    _onCancel()
                    if (completeOnCancel) {
                        while (nodes.isNotEmpty()) nodes.removeFirst().executeImmediately()
                    }
                }
            }
        }
    }

    private suspend inline fun executeLoopedOrNot(crossinline code: suspend () -> Unit) {
        if (looped) {
            val nodesCopy = nodes.toList()
            while (true) {
                code.invoke()
                nodes.addAll(nodesCopy)
            }
        } else {
            code.invoke()
        }
    }

    override fun executeImmediately() {
        if (!initialized) {
            init(this)
            initialized = true
        }
        while (nodes.isNotEmpty()) nodes.removeFirst().executeImmediately()
    }

    inline fun parallel(
        time: TimeSpan = this.time,
        speed: Double = this.speed,
        easing: Easing = this.easing,
        completeOnCancel: Boolean = this.completeOnCancel,
        looped: Boolean = false,
        callback: @AnimatorDslMarker Animator.() -> Unit
    ) = Animator(root, time, speed, easing, completeOnCancel, NodeKind.Parallel, looped).apply(callback).also { nodes.add(it) }

    inline fun sequence(
        time: TimeSpan = this.time,
        speed: Double = this.speed,
        easing: Easing = this.easing,
        completeOnCancel: Boolean = this.completeOnCancel,
        looped: Boolean = false,
        callback: @AnimatorDslMarker Animator.() -> Unit
    ) = Animator(root, time, speed, easing, completeOnCancel, NodeKind.Sequence, looped).apply(callback).also { nodes.add(it) }

    fun parallelLazy(
        time: TimeSpan = this.time,
        speed: Double = this.speed,
        easing: Easing = this.easing,
        completeOnCancel: Boolean = this.completeOnCancel,
        looped: Boolean = false,
        init: @AnimatorDslMarker Animator.() -> Unit
    ) = Animator(root, time, speed, easing, completeOnCancel, NodeKind.Parallel, looped, init).also { nodes.add(it) }

    fun sequenceLazy(
        time: TimeSpan = this.time,
        speed: Double = this.speed,
        easing: Easing = this.easing,
        completeOnCancel: Boolean = this.completeOnCancel,
        looped: Boolean = false,
        init: @AnimatorDslMarker Animator.() -> Unit
    ) = Animator(root, time, speed, easing, completeOnCancel, NodeKind.Sequence, looped, init).also { nodes.add(it) }

    inner class TweenNode(
        val view: View,
        vararg val vs: V2<*>,
        val lazyVs: Array<out () -> V2<*>>? = null,
        val time: TimeSpan = 1000.milliseconds,
        val lazyTime: (() -> TimeSpan)? = null,
        val easing: Easing
    ) : BaseAnimatorNode {

        val computedVs by lazy {
            if (lazyVs != null) Array(lazyVs.size) { lazyVs[it]() } else vs
        }

        override suspend fun execute() {
            try {
                val rtime = lazyTime?.invoke() ?: time
                view.tween(*computedVs, time = rtime, easing = easing)
            } catch (e: CancellationException) {
                //println("TweenNode: $e")
                if (completeOnCancel) {
                    executeImmediately()
                }
            }
        }

        override fun executeImmediately() = computedVs.fastForEach { it.set(1.0) }
    }

    @PublishedApi
    internal fun __tween(vararg vs: V2<*>, lazyVs: Array<out () -> V2<*>>? = null, time: TimeSpan = this.time, lazyTime: (() -> TimeSpan)? = null, easing: Easing = this.easing) {
        nodes.add(TweenNode(root, *vs, lazyVs = lazyVs, time = time, lazyTime = lazyTime, easing = easing))
    }

    @PublishedApi
    internal fun __tween(vararg vs: () -> V2<*>, time: TimeSpan = this.time, lazyTime: (() -> TimeSpan)? = null, easing: Easing = this.easing) {
        nodes.add(TweenNode(root, lazyVs = vs, time = time, lazyTime = lazyTime, easing = easing))
    }

    fun tween(vararg vs: V2<*>, time: TimeSpan = this.time, easing: Easing = this.easing) = __tween(*vs, time = time, easing = easing)
    fun tween(vararg vs: V2<*>, time: () -> TimeSpan = { this.time }, easing: Easing = this.easing) = __tween(*vs, lazyTime = time, easing = easing)

    fun tween(vararg vs: () -> V2<*>, time: TimeSpan = this.time, easing: Easing = this.easing) = __tween(*vs, time = time, easing = easing)
    fun tween(vararg vs: () -> V2<*>, time: () -> TimeSpan = { this.time }, easing: Easing = this.easing) = __tween(*vs, lazyTime = time, easing = easing)

    fun View.scaleBy(scaleX: Double, scaleY: Double = scaleX, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween({ this::scaleX[this.scaleX + scaleX] }, { this::scaleY[this.scaleY + scaleY] }, time = time, easing = easing)
    fun View.rotateBy(rotation: Angle, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween({ this::rotation[this.rotation + rotation] }, time = time, easing = easing)
    fun View.moveBy(x: Double = 0.0, y: Double = 0.0, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween({ this::x[this.x + x] }, { this::y[this.y + y] }, time = time, easing = easing)
    fun View.moveByWithSpeed(x: Double = 0.0, y: Double = 0.0, speed: Double = this@Animator.speed, easing: Easing = this@Animator.easing) = __tween({ this::x[this.x + x] }, { this::y[this.y + y] }, lazyTime = { (hypot(x, y) / speed.toDouble()).seconds }, easing = easing)

    fun View.scaleTo(scaleX: Double, scaleY: Double, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween(this::scaleX[scaleX], this::scaleY[scaleY], time = time, easing = easing)
    fun View.rotateTo(angle: Angle, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween(this::rotation[angle], time = time, easing = easing)
    fun View.moveTo(x: Double, y: Double, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween(this::x[x], this::y[y], time = time, easing = easing)
    fun View.moveToWithSpeed(x: Double, y: Double, speed: Double = this@Animator.speed, easing: Easing = this@Animator.easing) = __tween(this::x[x], this::y[y], lazyTime = { (hypot(this.x - x, this.y - y) / speed.toDouble()).seconds }, easing = easing)
    fun View.alpha(alpha: Double, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = __tween(this::alpha[alpha], time = time, easing = easing)

    fun View.scaleTo(scaleX: () -> Number, scaleY: () -> Number = scaleX, time: TimeSpan = this@Animator.time, lazyTime: (() -> TimeSpan)? = null, easing: Easing = this@Animator.easing) = __tween({ this::scaleX[scaleX()] }, { this::scaleY[scaleY()] }, time = time, lazyTime = lazyTime, easing = easing)
    fun View.rotateTo(rotation: () -> Angle, time: TimeSpan = this@Animator.time, lazyTime: (() -> TimeSpan)? = null, easing: Easing = this@Animator.easing) = __tween({ this::rotation[rotation()] }, time = time, lazyTime = lazyTime, easing = easing)
    fun View.moveTo(x: () -> Number = { this.x }, y: () -> Number = { this.y }, time: TimeSpan = this@Animator.time, lazyTime: (() -> TimeSpan)? = null, easing: Easing = this@Animator.easing) = __tween({ this::x[x()] }, { this::y[y()] }, time = time, lazyTime = lazyTime, easing = easing)
    fun View.moveToWithSpeed(x: () -> Number = { this.x }, y: () -> Number = { this.y }, speed: () -> Number = { this@Animator.speed }, easing: Easing = this@Animator.easing) = __tween({ this::x[x()] }, { this::y[y()] }, lazyTime = { (hypot(this.x - x().toDouble(), this.y - y().toDouble()) / speed().toDouble()).seconds }, easing = easing)

    fun View.show(time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = alpha(1, time, easing)
    fun View.hide(time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = alpha(0, time, easing)

    @Deprecated("Kotlin/Native boxes inline+Number")
    inline fun View.scaleTo(scaleX: Number, scaleY: Number = scaleX, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = scaleTo(scaleX.toDouble(), scaleY.toDouble(), time, easing)
    @Deprecated("Kotlin/Native boxes inline+Number")
    inline fun View.moveTo(x: Number, y: Number, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = moveTo(x.toDouble(), y.toDouble(), time, easing)
    @Deprecated("Kotlin/Native boxes inline+Number")
    inline fun View.moveToWithSpeed(x: Number, y: Number, speed: Number = this@Animator.speed, easing: Easing = this@Animator.easing) = moveToWithSpeed(x.toDouble(), y.toDouble(), speed.toDouble(), easing)
    @Deprecated("Kotlin/Native boxes inline+Number")
    inline fun View.alpha(alpha: Number, time: TimeSpan = this@Animator.time, easing: Easing = this@Animator.easing) = alpha(alpha.toDouble(), time, easing)

    fun wait(time: TimeSpan = this.time) = __tween(time = time)
    fun wait(time: () -> TimeSpan) = __tween(lazyTime = time)

    fun block(callback: () -> Unit) {
        nodes.add(object : BaseAnimatorNode {
            override suspend fun execute() = callback()
            override fun executeImmediately() = callback()
        })
    }
}

fun View.animator(
    time: TimeSpan = Animator.DEFAULT_TIME,
    speed: Double = Animator.DEFAULT_SPEED,
    easing: Easing = Animator.DEFAULT_EASING,
    completeOnCancel: Boolean = Animator.DEFAULT_COMPLETE_ON_CANCEL,
    kind: Animator.NodeKind = Animator.NodeKind.Sequence,
    looped: Boolean = false,
    block: @AnimatorDslMarker Animator.() -> Unit = {}
): Animator = Animator(this, time, speed, easing, completeOnCancel, kind, looped).apply(block)

suspend fun View.launchAnimate(
    time: TimeSpan = Animator.DEFAULT_TIME,
    speed: Double = Animator.DEFAULT_SPEED,
    easing: Easing = Animator.DEFAULT_EASING,
    completeOnCancel: Boolean = Animator.DEFAULT_COMPLETE_ON_CANCEL,
    kind: Animator.NodeKind = Animator.NodeKind.Sequence,
    looped: Boolean = false,
    block: @AnimatorDslMarker Animator.() -> Unit = {}
): Job = launchImmediately(coroutineContext) { animate(time, speed, easing, completeOnCancel, kind, looped, block) }

suspend fun View.animate(
    time: TimeSpan = Animator.DEFAULT_TIME,
    speed: Double = Animator.DEFAULT_SPEED,
    easing: Easing = Animator.DEFAULT_EASING,
    completeOnCancel: Boolean = Animator.DEFAULT_COMPLETE_ON_CANCEL,
    kind: Animator.NodeKind = Animator.NodeKind.Sequence,
    looped: Boolean = false,
    block: @AnimatorDslMarker Animator.() -> Unit = {}
): Animator = Animator(this, time, speed, easing, completeOnCancel, kind, looped).apply(block).also { it.execute() }

suspend fun View.animateSequence(
    time: TimeSpan = Animator.DEFAULT_TIME,
    speed: Double = Animator.DEFAULT_SPEED,
    easing: Easing = Animator.DEFAULT_EASING,
    completeOnCancel: Boolean = Animator.DEFAULT_COMPLETE_ON_CANCEL,
    looped: Boolean = false,
    block: @AnimatorDslMarker Animator.() -> Unit = {}
): Animator = animate(time, speed, easing, completeOnCancel, Animator.NodeKind.Sequence, looped, block)

suspend fun View.animateParallel(
    time: TimeSpan = Animator.DEFAULT_TIME,
    speed: Double = Animator.DEFAULT_SPEED,
    easing: Easing = Animator.DEFAULT_EASING,
    completeOnCancel: Boolean = Animator.DEFAULT_COMPLETE_ON_CANCEL,
    looped: Boolean = false,
    block: @AnimatorDslMarker Animator.() -> Unit = {}
): Animator = animate(time, speed, easing, completeOnCancel, Animator.NodeKind.Parallel, looped, block)
