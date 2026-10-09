package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [compute] – a light map, a scene: bitmaps worth tens of milliseconds – made off the main
 * thread, so no fade stutters while it is made; until it is done the last result stays (null at
 * first). In a still (a test's capture) it is made right away, so the picture has it.
 */
@Composable
fun <T : Any> rememberOffMain(vararg keys: Any?, compute: () -> T?): T? {
    if (LocalStill.current) return remember(*keys) { compute() }
    return produceState<T?>(initialValue = null, *keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }.value
}
