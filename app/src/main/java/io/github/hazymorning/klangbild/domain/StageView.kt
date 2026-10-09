package io.github.hazymorning.klangbild.domain

/** How the stage shows what plays. */
enum class StageView {
    /** One picture on the wall, lit by its own light. */
    SINGLE,

    /** Cover Flow: the picture in the middle, those before it to the left, those to come to the right. */
    FLOW,
}
