package com.soywiz.korge.intellij.editor

import com.soywiz.korau.sound.readNativeSound
import com.soywiz.korge.animate.AnSimpleAnimation
import com.soywiz.korge.animate.serialization.readAni
import com.soywiz.korge.audio.soundSystem
import com.soywiz.korge.ext.lipsync.readVoice
import com.soywiz.korge.ext.particle.readParticle
import com.soywiz.korge.ext.spriter.readSpriterLibrary
import com.soywiz.korge.ext.swf.SWFExportConfig
import com.soywiz.korge.ext.swf.readSWF
import com.soywiz.korge.ext.tiled.createView
import com.soywiz.korge.ext.tiled.readTiledMap
import com.soywiz.korge.input.onClick
import com.soywiz.korge.render.readTexture
import com.soywiz.korge.resources.ResourcesRoot
import com.soywiz.korge.scene.Module
import com.soywiz.korge.scene.Scene
import com.soywiz.korge.ui.UIFactory
import com.soywiz.korge.ui.button
import com.soywiz.korge.view.Container
import com.soywiz.korge.view.image
import com.soywiz.korge.view.text
import com.soywiz.korim.color.Colors
import com.soywiz.korim.vector.Context2d
import com.soywiz.korio.async.go
import com.soywiz.korio.async.spawnAndForget
import com.soywiz.korio.error.ignoreErrors
import com.soywiz.korio.inject.AsyncInjector
import com.soywiz.korio.vfs.jvm.ResourcesVfsProviderJvm
import com.soywiz.korma.geom.Anchor
import com.soywiz.korui.light.defaultLight
import java.io.File


abstract class KorgeBaseFileEditorProvider : com.intellij.openapi.fileEditor.FileEditorProvider {
	companion object {
		val pluginClassLoader by lazy { KorgeBaseFileEditorProvider::class.java.classLoader }
		val pluginResurcesVfs by lazy { ResourcesVfsProviderJvm().invoke(pluginClassLoader) }
	}

	override fun createEditor(project: com.intellij.openapi.project.Project, virtualFile: com.intellij.openapi.vfs.VirtualFile): com.intellij.openapi.fileEditor.FileEditor {
		return KorgeBaseKorgeFileEditor(project, virtualFile, EditorModule, "Preview")
	}

	override fun getEditorTypeId(): String = this::class.java.name

	object EditorModule : Module() {
		override val mainScene: Class<out Scene> = EditorScene::class.java

		suspend override fun init(injector: AsyncInjector) {
			super.init(injector)
			injector.get<ResourcesRoot>().mount("/", pluginResurcesVfs.root)
		}

		class EditorScene(
			val fileToEdit: KorgeFileToEdit,
			val ui: UIFactory
		) : Scene() {
			suspend private fun getLipTexture(char: Char) = ignoreErrors { pluginResurcesVfs["/com/soywiz/korge/intellij/lips/ken-$char.png"].readTexture(views) } ?: views.transparentTexture

			suspend override fun sceneInit(sceneView: Container) {
				val loading = views.text("Loading...", color = Colors.WHITE).apply {
					//format = Html.Format(align = Html.Alignment.CENTER)
					//x = views.virtualWidth * 0.5
					//y = views.virtualHeight * 0.5
					x = 16.0
					y = 16.0
				}
				sceneView += loading

				spawnAndForget {
					views.eventLoop.sleepNextFrame()
					val file = fileToEdit.file

					when (file.extensionLC) {
						"tmx" -> {
							val tiled = file.readTiledMap(views)
							sceneView += tiled.createView(views)
							views.setVirtualSize(tiled.pixelWidth, tiled.pixelHeight)
						}
						"svg" -> {
							sceneView += views.image(file.readTexture(views))
						}
						"scml" -> {
							val spriterLibrary = file.readSpriterLibrary(views)
							val spriterView = spriterLibrary.create(spriterLibrary.entityNames.first()).apply {
								x = views.virtualWidth * 0.5
								y = views.virtualHeight * 0.5
							}
							sceneView += spriterView
						}
						"pex" -> {
							sceneView += fileToEdit.file.readParticle(views).create(views.virtualWidth / 2.0, views.virtualHeight / 2.0)
						}
						"wav", "mp3", "ogg", "lipsync" -> {
							if (file.basename.contains("voice") || file.basename.contains("lipsync")) {
								val wav = file.withExtension("wav")
								val mp3 = file.withExtension("mp3")
								val ogg = file.withExtension("ogg")
								val audios = listOf(wav, mp3, ogg)
								val audio = audios.firstOrNull { it.exists() }
								val voice = audio?.readVoice(views)
								//audio?.readAudioData()?.play()

								//val classLoader = pluginClassLoader


								val voiceName = "voice"

								views.setVirtualSize(150 * 2, 100 * 2)

								val mouth = AnSimpleAnimation(views, 10, mapOf(
									"A" to listOf(getLipTexture('A')),
									"B" to listOf(getLipTexture('B')),
									"C" to listOf(getLipTexture('C')),
									"D" to listOf(getLipTexture('D')),
									"E" to listOf(getLipTexture('E')),
									"F" to listOf(getLipTexture('F')),
									"G" to listOf(getLipTexture('G')),
									"H" to listOf(getLipTexture('H')),
									"X" to listOf(getLipTexture('X'))
								), Anchor.MIDDLE_CENTER).apply {
									x = views.virtualWidth * 0.5
									y = views.virtualHeight * 0.5
									addProp("lipsync", voiceName)
								}

								sceneView += mouth

								go {
									voice?.play(voiceName)
								}

								//sceneView.addEventListener<LipSyncEvent> { e ->
								//	mouth.tex = lips[e.lip] ?: views.transparentTexture
								//}
							} else {
								views.soundSystem.play(file.readNativeSound())
							}

							Unit
						}
						"swf", "ani" -> {
							val animationLibrary = when (file.extensionLC) {
								"swf" -> file.readSWF(views, defaultConfig = SWFExportConfig(
									mipmaps = false,
									antialiasing = true,
									rasterizerMethod = Context2d.ShapeRasterizerMethod.X4,
									exportScale = 1.0,
									exportPaths = false
								))
								"ani" -> file.readAni(views)
								else -> null
							}

							if (animationLibrary != null) {
								views.setVirtualSize(animationLibrary.width, animationLibrary.height)
								sceneView += animationLibrary.createMainTimeLine()
								sceneView += views.text("${file.basename} : ${animationLibrary.width}x${animationLibrary.height}").apply {
									x = 16.0
									y = 16.0
								}
							}
						}
					}
					sceneView -= loading

					sceneView += ui.button("Open").apply {
						width = 100.0
						height = 24.0
						x = views.virtualWidth - width
						y = 0.0
						onClick {
							defaultLight.open(File(file.absolutePath))
						}
					}

					Unit
				}
			}
		}
	}
}
