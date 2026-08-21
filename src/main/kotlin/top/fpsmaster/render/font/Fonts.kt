package top.fpsmaster.render.font;

//? if >= 1.21.11 {
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
//? }

class Fonts {
    companion object {
        //? if >= 1.21.11 {
        val fontJetBrainsMono10 = FontDescription.Resource(Identifier.fromNamespaceAndPath("fpsmaster", "jetbrains_mono10"))
        val ui = FontDescription.Resource(Identifier.fromNamespaceAndPath("fpsmaster", "ui"))
        //? }
    }
}
