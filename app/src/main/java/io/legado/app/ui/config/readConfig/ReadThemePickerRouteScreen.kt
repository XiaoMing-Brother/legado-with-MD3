package io.legado.app.ui.config.readConfig

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReadThemePickerRouteScreen(
    onBackClick: () -> Unit,
    viewModel: ReadThemePickerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val appliedThemeIds by viewModel.appliedThemeIds.collectAsStateWithLifecycle()
    val readingStyles by viewModel.readingStyles.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ReadThemePickerEffect.BackgroundFailed -> {
                    Toast.makeText(
                        context,
                        R.string.read_theme_image_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    ReadThemePickerScreen(
        onBackClick = onBackClick,
        appliedThemeIds = appliedThemeIds,
        readingStyles = readingStyles,
        onSelectTheme = viewModel::applyTheme,
        onSelectSavedStyle = viewModel::selectStyle,
    )
}
