package io.github.teamclouday.androidMic.ui.home.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import io.github.teamclouday.androidMic.ProcessingMode
import io.github.teamclouday.androidMic.ui.MainViewModel

@Composable
fun DialogProcessingMode(
    vm: MainViewModel,
    expanded: MutableState<Boolean>,
) {
    DialogList(
        expanded,
        enum = ProcessingMode.entries,
        onClick = { vm.setProcessingMode(it) },
        text = { it.name }
    )
}
