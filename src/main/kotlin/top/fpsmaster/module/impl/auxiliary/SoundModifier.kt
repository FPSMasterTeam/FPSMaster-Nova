package top.fpsmaster.module.impl.auxiliary

import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class SoundModifier : Module("sound-modifier", Category.AUXILIARY) {
    init {
        values.addAll(
            arrayOf(
                blocks,
                hostile,
                neutral,
                players,
                ambient,
                liquid,
                dig,
                game,
                mob
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false

        val blocks = NumberValue("blocks", 1.0, 0.0, 1.0, 0.01)
        val hostile = NumberValue("hostile", 1.0, 0.0, 1.0, 0.01)
        val neutral = NumberValue("neutral", 1.0, 0.0, 1.0, 0.01)
        val players = NumberValue("players", 1.0, 0.0, 1.0, 0.01)
        val ambient = NumberValue("ambient", 1.0, 0.0, 1.0, 0.01)
        val liquid = NumberValue("liquid", 1.0, 0.0, 1.0, 0.01)
        val dig = NumberValue("dig", 1.0, 0.0, 1.0, 0.01)
        val game = NumberValue("game", 1.0, 0.0, 1.0, 0.01)
        val mob = NumberValue("mob", 1.0, 0.0, 1.0, 0.01)

        @JvmStatic
        fun adjustVolume(instance: SoundInstance): Float {
            val volume = instance.volume
            if (!active) {
                return volume
            }

            //? if >=1.21.11 {
            val path = instance.identifier.path
            //?} else {
            /*val path = instance.location.path*/
            //?}
            val edgeMultiplier = when {
                path.startsWith("liquid") -> liquid.getValue()
                path.startsWith("dig") -> dig.getValue()
                path.startsWith("game") -> game.getValue()
                path.startsWith("mob") -> mob.getValue()
                else -> null
            }

            val multiplier = edgeMultiplier ?: when (instance.source) {
                SoundSource.BLOCKS -> blocks.getValue()
                SoundSource.HOSTILE -> hostile.getValue()
                SoundSource.NEUTRAL -> neutral.getValue()
                SoundSource.PLAYERS -> players.getValue()
                SoundSource.AMBIENT -> ambient.getValue()
                else -> 1.0
            }

            return (volume * multiplier).toFloat()
        }
    }
}
