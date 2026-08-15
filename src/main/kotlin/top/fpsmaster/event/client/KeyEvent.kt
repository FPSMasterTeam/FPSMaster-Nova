package top.fpsmaster.event.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.vlouboos.standaloneevent.api.Event

class KeyEvent(var key: InputConstants.Key) : Event()