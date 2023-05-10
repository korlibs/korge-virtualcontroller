import korlibs.event.GameButton
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.virtualcontroller.virtualController
import korlibs.math.geom.Point
import korlibs.math.geom.Vector2
import korlibs.math.isAlmostZero
import korlibs.time.TimeSpan
import korlibs.time.hz
import korlibs.time.milliseconds
import korlibs.time.seconds
import kotlin.math.absoluteValue

suspend fun main() = Korge {
    sceneContainer().changeTo({ MainMyModuleScene() })
}

class MainMyModuleScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val textInfo = text("")

        val virtualController = virtualController()
        var jumping = false
        var moving = false
        var playerPos = Point(0f, 0f)
        var gravity = Vector2(0f, 10f)
        var playerSpeed = Vector2(0f, 0f)

        fun tryMoveDelta(delta: Point): Boolean {
            val newPos = playerPos + delta
            if (newPos.y <= 0f) {
                playerPos = newPos
                return true
            } else {
                playerPos = playerPos.copy(y = 0f)
                return false
            }
        }

        var stateName = "-"
        fun setState(name: String, time: TimeSpan) {
            stateName = name
        }

        fun updateState() {
            when {
                jumping -> setState("jump", 0.1.seconds)
                moving -> setState("walk", 0.1.seconds)
                else -> setState("idle", 0.3.seconds)
            }
        }

        fun updated(right: Boolean, up: Boolean, scale: Float = 1f) {
            if (!up) {
                //player.scaleX = player.scaleX.absoluteValue * if (right) +1f else -1f
                tryMoveDelta(Point(2.0, 0) * (if (right) +1 else -1) * scale)
                //player.speed = 2f
                moving = true
            } else {
                //player.speed = 1f
                moving = false
            }
            updateState()
            //updateTextContainerPos()
        }


        virtualController.apply {
            down(GameButton.BUTTON_SOUTH) {
                val isInGround = playerSpeed.y.isAlmostZero()
                //if (isInGround) {
                if (true) {
                    if (!jumping) {
                        jumping = true
                        updateState()
                    }
                    playerSpeed += Vector2(0, -5.5)
                }
            }
            changed(GameButton.LX) {
                if (it.new.absoluteValue < 0.01f) {
                    updated(right = it.new > 0f, up = true, scale = 1f)
                }
            }
            addUpdater(60.hz) {
                val lx = virtualController.lx
                when {
                    lx < 0f -> {
                        updated(right = false, up = false, scale = lx.absoluteValue)
                    }
                    lx > 0f -> {
                        updated(right = true, up = false, scale = lx.absoluteValue)
                    }
                }
            }
        }

        val STEP = 16.milliseconds
        addFixedUpdater(STEP) {
            playerSpeed += gravity * STEP.seconds
            if (!tryMoveDelta(playerSpeed)) {
                playerSpeed = Vector2.ZERO
                if (jumping) {
                    jumping = false
                    updateState()
                }
            }
            textInfo.text = "state: $stateName, pos=$playerPos"
        }
    }
}