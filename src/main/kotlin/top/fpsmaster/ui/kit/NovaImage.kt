package top.fpsmaster.ui.kit

//? if >=1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation*/
//?}
import top.fpsmaster.uikit.canvas.ImageHandle

class NovaImage(
    //? if >=1.21.11 {
    val id: Identifier,
    //?} else {
    /*val id: ResourceLocation,*/
    //?}
    private val w: Int,
    private val h: Int
) : ImageHandle {
    override fun width(): Int = w
    override fun height(): Int = h
}
