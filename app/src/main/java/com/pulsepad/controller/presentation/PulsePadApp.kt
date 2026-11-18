package com.pulsepad.controller.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pulsepad.controller.R
import com.pulsepad.controller.presentation.ui.capability.CapabilityScreen
import com.pulsepad.controller.presentation.ui.debug.InputDebugScreen
import kotlinx.coroutines.launch

/**
 * Root composable.
 *
 * Phases 0 and 1 are exposed as two debug surfaces; the four-destination
 * navigation shell replaces this tab row in Phase 2.
 */
@Composable
fun PulsePadApp(modifier: Modifier = Modifier) {
    val pages = listOf(
        stringResource(R.string.input_debug_title),
        stringResource(R.string.capability_title)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        androidx.compose.foundation.layout.Column {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                pages.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> InputDebugScreen()
                    else -> CapabilityScreen()
                }
            }
        }
    }
}
