package korlibs.korge.virtualcontroller

import korlibs.datastructure.iterators.*
import korlibs.event.*
import korlibs.io.async.*
import korlibs.korge.input.*
import korlibs.korge.view.*
import korlibs.math.*
import korlibs.math.geom.*
import korlibs.memory.*
import korlibs.render.*
import korlibs.time.*

data class VirtualStickConfig(
    val left: Key,
    val right: Key,
    val up: Key,
    val down: Key,
    val lx: GameButton,
    val ly: GameButton,
    val position: Anchor,
) {
    companion object {
        val MAIN = VirtualStickConfig(
            left = Key.LEFT,
            right = Key.RIGHT,
            up = Key.UP,
            down = Key.DOWN,
            lx = GameButton.LX,
            ly = GameButton.LY,
            position = Anchor.BOTTOM_LEFT,
        )
    }
}

data class VirtualButtonConfig(
    val key: Key,
    val button: GameButton,
    val position: Anchor,
) {
    companion object {
        val SOUTH = VirtualButtonConfig(
            key = Key.SPACE,
            button = GameButton.BUTTON_SOUTH,
            position = Anchor.BOTTOM_RIGHT,
        )
    }
}

fun Container.virtualController(
    sticks: List<VirtualStickConfig> = listOf(VirtualStickConfig.MAIN),
    buttons: List<VirtualButtonConfig> = listOf(VirtualButtonConfig.SOUTH),
): VirtualController {
    val container = container()
    val controller = VirtualController(container)
    val rect = Rectangle.fromBounds(128f, 128f, width - 128f, height - 128f)
    val keyStickControllers = arrayListOf<KeyboardStickController>()
    val keyButtonControllers = arrayListOf<KeyboardButtonController>()
    val stickByButton = mutableMapOf<GameButton, VirtualStickView>()
    val buttonByButton = mutableMapOf<GameButton, VirtualButtonView>()

    gamepad {
        val lastGamepadInfo = GamepadInfo()
        updatedGamepad {
            for (button in GameButton.BUTTONS) {
                if (it[button] != lastGamepadInfo[button]) {
                    stickByButton[button]?.let { view ->
                        view.updateXPos(Point(it[view.buttonX], -it[view.buttonY]))
                    }
                    buttonByButton[button]?.update(it[button] != 0f)
                }
            }
            lastGamepadInfo.copyFrom(it)
        }
    }
    for (stick in sticks) {
        val virtualStickView =
            VirtualStickView(controller = controller, buttonX = stick.lx, buttonY = stick.ly, radius = 92f)
                .xy(rect.getAnchoredPoint(stick.position))

        stickByButton[stick.lx] = virtualStickView
        stickByButton[stick.ly] = virtualStickView

        container += virtualStickView

        keyStickControllers += KeyboardStickController(
            stick,
            virtualStickView = virtualStickView
        )
    }
    for (button in buttons) {
        val virtualButtonView = VirtualButtonView(controller = controller, button = button.button, radius = 92f)
            .xy(rect.getAnchoredPoint(button.position))
        container += virtualButtonView
        buttonByButton[button.button] = virtualButtonView
        keyButtonControllers += KeyboardButtonController(
            button,
            virtualButtonView = virtualButtonView
        )
    }

    addFixedUpdater(16.milliseconds) {
        keyStickControllers.fastForEach { it.update(this, controller) }
        keyButtonControllers.fastForEach { it.update(this, controller) }
        controller.updated()
    }
    return controller
}

class KeyboardButtonController(
    val config: VirtualButtonConfig,
    val virtualButtonView: VirtualButtonView?
) {
    var usedKeyboard = false

    fun update(view: View, controller: VirtualController) {
        val keys = view.stage?.input?.keys ?: return
        val keyPressed = keys[config.key]
        if (keyPressed) {
            usedKeyboard = true
        }
        if (usedKeyboard) {
            controller[config.button] = keyPressed.toInt().toFloat()
            virtualButtonView?.update(keyPressed)
        }
        if (!keyPressed) {
            usedKeyboard = false
        }
    }
}

class KeyboardStickController(
    val config: VirtualStickConfig,
    val virtualStickView: VirtualStickView?
) {
    var lxKey: Float = 0f
    var lyKey: Float = 0f
    var usedKeyboard = false

    fun update(view: View, controller: VirtualController) {
        val keys = view?.stage?.input?.keys ?: return@update
        val keyPressed = keys[config.left] || keys[config.right] || keys[config.up] || keys[config.down]
        if (keyPressed) {
            usedKeyboard = true
        }
        when {
            keys[config.left] -> lxKey -= .125f
            keys[config.right] -> lxKey += .125f
            else -> lxKey /= 2f
        }
        when {
            keys[config.up] -> lyKey -= .125f
            keys[config.down] -> lyKey += .125f
            else -> lyKey /= 2f
        }
        lxKey = lxKey.clamp(-1f, +1f).normalizeAlmostZero()
        lyKey = lyKey.clamp(-1f, +1f).normalizeAlmostZero()
        if (usedKeyboard) {
            controller[config.lx] = lxKey
            controller[config.ly] = lyKey
            virtualStickView?.updateXPos(Point(lxKey, lyKey))
        }
        if (usedKeyboard && lxKey == 0f && lyKey == 0f && !keyPressed) {
            usedKeyboard = false
        }
    }
}

class VirtualController(
    val container: Container
) {
    private val oldGamePadInfo = GamepadInfo()
    val gamePadInfo = GamepadInfo()

    var lx: Float get() = this[GameButton.LX]; set(value) { this[GameButton.LX] = value }
    var ly: Float get() = this[GameButton.LY]; set(value) { this[GameButton.LY] = value }

    var rx: Float get() = this[GameButton.RX]; set(value) { this[GameButton.RX] = value }
    var ry: Float get() = this[GameButton.RY]; set(value) { this[GameButton.RY] = value }

    operator fun get(button: GameButton): Float = gamePadInfo.rawButtons[button.index]
    operator fun set(button: GameButton, value: Float) { gamePadInfo.rawButtons[button.index] = value }

    //fun dispatch(gameWindow: GameWindow) {
    //    gameWindow.dispatchGamepadUpdateStart()
    //    gameWindow.dispatchGamepadUpdateAdd(gamePadInfo)
    //    gameWindow.dispatchGamepadUpdateEnd()
    //}

    data class ChangedEvent(
        var button: GameButton = GameButton.LX,
        var old: Float = 0f,
        var new: Float = 0f,
    ) {
        val oldBool get() = old != 0f
        val newBool get() = new != 0f
    }

    private val changedEvent = ChangedEvent()
    val onChanged = Signal<ChangedEvent>()

    fun changed(button: GameButton, block: (ChangedEvent) -> Unit) = onChanged.add { if (it.button == button) block(it) }
    fun down(button: GameButton, block: () -> Unit) = changed(button) { if (!it.oldBool && it.newBool) block() }
    fun up(button: GameButton, block: () -> Unit) = changed(button) { if (it.oldBool && !it.newBool) block() }

    fun updated() {
        GameButton.BUTTONS.fastForEach {
            val old = oldGamePadInfo[it]
            val new = gamePadInfo[it]
            if (old != new) {
                changedEvent.button = it
                changedEvent.old = old
                changedEvent.new = new
                onChanged(changedEvent)
            }
        }
        oldGamePadInfo.copyFrom(gamePadInfo)
    }
}

class VirtualStickView(
    val controller: VirtualController,
    val buttonX: GameButton = GameButton.LX,
    val buttonY: GameButton = GameButton.LY,
    radius: Float = 64f
) : Container() {
    val circleOut = fastEllipse(Size(radius * 1.5f, radius * 1.5f)).anchor(Anchor.CENTER).also { it.alpha = 0.5f }
    val circle = fastEllipse(Size(radius, radius)).anchor(Anchor.CENTER).also { it.alpha = 0.75f }
    var radius: Float
        get() = circle.radius.width
        set(value) {
            circle.radius = Size(value, value)
            circleOut.radius = Size(value * 1.5f, value * 1.5f)
        }

    fun updateXPos(pos: Point) {
        updatePos(pos * radius)
    }

    fun updatePos(pos: Point) {
        val polar = Point.polar(pos.angle)
        val magnitudeScale = (pos.magnitude / radius).clamp01()
        val scaledPolar = polar * magnitudeScale
        circle.pos = scaledPolar * radius
        controller[buttonX] = scaledPolar.x
        controller[buttonY] = scaledPolar.y
    }

    init {
        //val text = text("hello")

        singleTouch {
            start {
            }
            moveAnywhere {
                updatePos(it.local - it.startLocal)
            }
            endAnywhere {
                updatePos(Point(0, 0))
            }
        }
    }
}

class VirtualButtonView(
    val controller: VirtualController,
    val button: GameButton = GameButton.XBOX_A,
    radius: Float = 64f
) : Container() {
    val circle = fastEllipse(Size(radius, radius)).anchor(Anchor.CENTER).also { it.alpha = 0.8f }
    var radius: Float by circle::radiusAvg

    fun update(pressed: Boolean) {
        controller[button] = pressed.toInt().toFloat()
        circle.alpha = if (pressed) 1f else .8f
    }

    init {
        //val text = text("hello")

        singleTouch {
            start {
                update(pressed = true)
            }
            endAnywhere {
                update(pressed = false)
            }
        }
    }
}

