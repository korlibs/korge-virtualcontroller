import korlibs.event.GameButton
import korlibs.event.Key
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.virtualcontroller.VirtualButtonConfig
import korlibs.korge.virtualcontroller.VirtualStickConfig
import korlibs.korge.virtualcontroller.virtualController
import korlibs.math.geom.*
import korlibs.math.isAlmostZero
import korlibs.time.TimeSpan
import korlibs.time.hz
import korlibs.time.milliseconds
import korlibs.time.seconds
import kotlin.math.absoluteValue

suspend fun main() = Korge(windowSize = Size(512, 512)) {
    sceneContainer().changeTo({ MainMyModuleScene() })
}

class MainMyModuleScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val textInfo = text("")

        val virtualController = virtualController(
            sticks = listOf(
                VirtualStickConfig(
                    left = Key.LEFT,
                    right = Key.RIGHT,
                    up = Key.UP,
                    down = Key.DOWN,
                    lx = GameButton.LX,
                    ly = GameButton.LY,
                    anchor = Anchor.BOTTOM_LEFT,
                    offset = Point(0, 0),
                )
            ),
            buttons = listOf(
                VirtualButtonConfig(
                    key = Key.SPACE,
                    button = GameButton.BUTTON_SOUTH,
                    anchor = Anchor.BOTTOM_RIGHT,
                    offset = Point(0, 0),
                ),
                VirtualButtonConfig(
                    key = Key.RETURN,
                    button = GameButton.BUTTON_NORTH,
                    anchor = Anchor.BOTTOM_RIGHT,
                    offset = Point(0, -150),
                )
            ),
        )
        var jumping = false
        var moving = false
        var playerPos = Point(0, 0)
        var gravity = Vector2D(0, 10)
        var playerSpeed = Vector2D(0, 0)

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
                    playerSpeed += Vector2D(0, -5.5)
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
                playerSpeed = Vector2D.ZERO
                if (jumping) {
                    jumping = false
                    updateState()
                }
            }
            textInfo.text = "state: $stateName, pos=$playerPos"
        }
    }
}
