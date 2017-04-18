package com.soywiz.korge.sample

import com.soywiz.korge.Korge
import com.soywiz.korge.animate.AnLibrary
import com.soywiz.korge.animate.AnTextField
import com.soywiz.korge.bitmapfont.BitmapFont
import com.soywiz.korge.bitmapfont.FontDescriptor
import com.soywiz.korge.component.Component
import com.soywiz.korge.component.docking.dockedTo
import com.soywiz.korge.ext.spriter.SpriterLibrary
import com.soywiz.korge.input.mouse
import com.soywiz.korge.input.onClick
import com.soywiz.korge.input.onOut
import com.soywiz.korge.input.onOver
import com.soywiz.korge.render.Texture
import com.soywiz.korge.resources.Path
import com.soywiz.korge.resources.ResourcesRoot
import com.soywiz.korge.scene.Module
import com.soywiz.korge.scene.Scene
import com.soywiz.korge.time.timers
import com.soywiz.korge.tween.Easing
import com.soywiz.korge.tween.Easings
import com.soywiz.korge.tween.rangeTo
import com.soywiz.korge.tween.tween
import com.soywiz.korge.view.*
import com.soywiz.korge.view.tiles.TileSet
import com.soywiz.korge.view.tiles.tileMap
import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korio.async.async
import com.soywiz.korio.async.go
import com.soywiz.korio.async.sleep
import com.soywiz.korio.inject.AsyncInjector
import com.soywiz.korio.vfs.ResourcesVfs
import com.soywiz.korma.geom.Anchor

object Sample1 {
	@JvmStatic fun main(args: Array<String>) = Korge(Sample1Module, args, sceneClass = Sample1Scene::class.java)
	//@JvmStatic fun main(args: Array<String>) = Korge(Sample1Module, args, sceneClass = Sample2Scene::class.java)
}

object Sample1Module : Module() {
	override val title = "Sample1"
	override val icon = "kotlin8.png"
	override var mainScene = Sample1Scene::class.java
	//override var mainScene = Sample2Scene::class.java

	suspend override fun init(injector: AsyncInjector) {
		injector.get<ResourcesRoot>().mount("/", ResourcesVfs)
		injector.get<Views>().registerPropertyTrigger("gravity") { view, key, value ->
			val gravity = value.toDouble()
			go {
				var speed = 0.0
				val stepMs = 16
				while (true) {
					speed += (gravity / 1000.0) * stepMs
					view.y += speed
					view.timers.waitMilliseconds(stepMs)
				}
			}
			//println(child)
		}
	}
}

class MouseSampleController(view: View) : Component(view) {
	override fun update(dtMs: Int) {
		//view.globalToLocal(views.input.mouse, temp)
		view.x = view.parent?.localMouseX ?: 0.0
		view.y = view.parent?.localMouseY ?: 0.0
		view.rotationDegrees = (view.rotationDegrees + 1) % 360
	}
}

fun View.mouseSampleController() = this.apply { MouseSampleController(this).attach() }

class Sample2Scene(
	@Path("test4.swf") val test4Library: AnLibrary
) : Scene() {
	suspend override fun init() {
		super.init()

		this.sceneView += test4Library.createMainTimeLine()
	}
}

class JellyButton(val view: View) {
	val initialScale = view.scale

	//val thread = AsyncThread()

	init {
		view.onOver { async { view.tween(View::scale..initialScale * 1.5, time = 200, easing = Easings.EASE_OUT_ELASTIC) } }
		view.onOut { async { view.tween(View::scale..initialScale, time = 400, easing = Easings.EASE_OUT_ELASTIC) } }
	}

	fun onClick(callback: suspend () -> Unit) {
		view.onClick { async { callback() } }
	}
}

class Sample1Scene(
	@Path("korge.png") val korgeTex: Texture,
	@Path("simple.swf") val swfLibrary: AnLibrary,
	@Path("test1.swf") val test1Library: AnLibrary,
	@Path("test4.swf") val test4Library: AnLibrary,
	@Path("as3test.swf") val as3testLibrary: AnLibrary,
	@Path("soundtest.swf") val soundtestLibrary: AnLibrary,
	@Path("progressbar.swf") val progressbarLibrary: AnLibrary,
	@Path("buttons.swf") val buttonsLibrary: AnLibrary,
	@Path("props.swf") val propsLibrary: AnLibrary,
	@Path("tiles.png") val tilesetTex: Texture,
	@Path("font/font.fnt") val font: BitmapFont,
	@Path("spriter-sample1/demo.scml") val demoSpriterLibrary: SpriterLibrary,
	@FontDescriptor(face = "Arial", size = 40) val font2: BitmapFont
) : Scene() {
	lateinit var tileset: TileSet
	lateinit var image: Image
	lateinit var percent: AnTextField

	suspend override fun init() {
		super.init()

		tileset = TileSet(tilesetTex, 32, 32)

		sceneView.container {
			//this.text(font, "hello")
			this.tileMap(Bitmap32(8, 8), tileset) {
				//blendMode = BlendMode.ADD
				this.x = -128.0
				this.y = -128.0
				alpha = 0.8
			}
		}.mouseSampleController()

		sceneView.container {
			this += swfLibrary.createMainTimeLine()
		}


		image = sceneView.image(korgeTex, 0.5).apply {
			scale = 0.2
			rotation = Math.toRadians(-90.0)
			alpha = 0.7
			//smoothing = false
			mouse.hitTestType = View.HitTestType.SHAPE
			onOver { alpha = 1.0 }
			onOut { alpha = 0.7 }
			//onDown { scale = 0.3 }
			//onUp { scale = 0.2 }
		}

		val tilemap = sceneView.tileMap(Bitmap32(8, 8), tileset) {
			alpha = 0.8
		}


		sceneView.container {
			val mc = test1Library.createMainTimeLine().apply {
				//speed = 0.1
			}
			this += mc
			//mc.addUpdatable { println(mc.dumpToString()) }
		}

		sceneView.container {
			//JekllyButton()
			val mc = test4Library.createMainTimeLine().apply {
				x = 320.0
				y = 320.0
				//speed = 0.1
			}
			this += mc
			//mc.addUpdatable { println(mc.dumpToString()) }
		}

		sceneView.container {
			val mc = as3testLibrary.createMainTimeLine().apply {
				//x = 320.0
				//y = 320.0
				//speed = 0.1
			}
			this += mc
			//mc.addUpdatable { println(mc.dumpToString()) }
		}

		sceneView.container {
			val mc = soundtestLibrary.createMainTimeLine().apply {
				//x = 320.0
				//y = 320.0
				//speed = 0.1
			}
			this += mc
			//mc.addUpdatable { println(mc.dumpToString()) }
		}

		sceneView += progressbarLibrary.createMainTimeLine().apply {
			this.dockedTo(Anchor.TOP_LEFT)
			go {
				percent = (this["percent"] as AnTextField?)!!
				percent.onClick {
					percent.alpha = 0.5
					println(percent.alpha)
				}
				sceneView.tween(time = 2000, easing = Easing.EASE_IN_OUT_QUAD) { ratio ->
					this.seekStill("progressbar", ratio)
					//println(this.findFirstWithName("percent"))
					percent.setText("%d%%".format((ratio * 100).toInt()))
				}
			}
		}

		sceneView.text(font, "Hello world! F,", textSize = 72.0).apply {
			blendMode = BlendMode.ADD
			x = 100.0
			y = 100.0
		}

		sceneView.text(font2, "2017", textSize = 40.0).apply {
			x = 0.0
			y = 0.0
		}

		go {
			image.tween(
				View::x..200.0, View::y..200.0,
				View::rotation..Math.toRadians(0.0), View::scale..2.0,
				time = 2000, easing = Easing.EASE_IN_OUT_QUAD
			)
			for (delta in listOf(+200.0, -200.0, +100.0)) {
				image.tween(View::x..image.x + delta, time = 1000, easing = Easing.EASE_IN_OUT_QUAD)
			}
			//views.dump()
		}

		val player = demoSpriterLibrary.create("Player", "idle").apply {
			//val player = demoSpriterLibrary.create("Player", "hurt_idle").apply {
			x = 400.0
			y = 200.0
			scale = 0.7
		}
		go {
			player.tween(
				View::rotationDegrees..360.0,
				View::scale..1.0,
				time = 1000, easing = Easing.EASE_IN_OUT_QUAD
			)
			player.changeTo("hurt_idle", time = 300, easing = Easing.EASE_IN)
			sleep(400)
			player.changeTo("walk", time = 1000, easing = Easing.LINEAR)
			sleep(400)
			player.changeTo("sword_swing_0", time = 1000, easing = Easing.LINEAR)
			sleep(500)
			player.changeTo("throw_axe", time = 500, easing = Easing.LINEAR)
			player.waitCompleted()
			println("completed")
			//player.speed = 0.1
			player.tween(View::speed..0.3, time = 2000)

			//println("${player.animation1}:${player.animation2}:${player.prominentAnimation}")
		}
		//sceneView += ShaderView(views).apply { this += player }
		sceneView += player

		sceneView.container {
			val mc = buttonsLibrary.createMainTimeLine()
			mc.scale = 0.3
			mc.setXY(400, 300)
			//this += ShaderView(views).apply { this += mc }
			this += mc
			for (n in 1..4) {
				for (m in 1..4) {
					val buttonView = mc["buttonsRow$n"]?.get("button$m")
					if (buttonView != null) {
						JellyButton(buttonView)
					}
				}
			}
		}

		sceneView += propsLibrary.createMainTimeLine()
	}
}
