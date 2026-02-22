package top.fpsmaster.command

abstract class Command(val identity: String) {
    abstract fun execute(args: Array<String>)
}