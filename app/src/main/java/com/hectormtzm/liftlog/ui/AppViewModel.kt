package com.hectormtzm.liftlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.LiftlogApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as LiftlogApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}
