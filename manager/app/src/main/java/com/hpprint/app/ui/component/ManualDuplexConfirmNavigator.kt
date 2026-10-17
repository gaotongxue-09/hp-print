package com.hpprint.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.util.ManualDuplexController
import com.hpprint.app.ui.util.ManualDuplexState

/**
 * 手动双面「翻面确认」导航器。
 * 用非生命周期感知的 collect 持续监听 [ManualDuplexController.state]，即使 App 处于后台也不停止，
 * 因此在后台打印完奇数页、需要翻面时，确认页已压入路由栈，用户从通知/图标回来会直接落在确认页。
 */
@Composable
fun ManualDuplexConfirmNavigator() {
    val navigator = LocalNavigator.current
    LaunchedEffect(Unit) {
        ManualDuplexController.state.collect { state ->
            when (state) {
                is ManualDuplexState.AwaitingFlip ->
                    navigator.push(Route.ManualDuplexConfirm(state.title))
                ManualDuplexState.Idle ->
                    navigator.popUntil { it !is Route.ManualDuplexConfirm }
            }
        }
    }
}