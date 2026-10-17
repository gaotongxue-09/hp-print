package com.hpprint.app.ui.screen.setup

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpprint.app.R
import com.hpprint.app.data.repository.SettingsRepositoryImpl
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.theme.AppSettings
import com.hpprint.app.ui.theme.ColorMode
import com.hpprint.app.ui.theme.HpPrintTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.ColorFilter
import com.hpprint.app.ui.component.miuix.effect.BgEffectBackground
import com.hpprint.app.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BrandGreen = Color(0xFF10B981)

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    var uiMode by remember { mutableStateOf(UiMode.Miuix) }
    var colorMode by remember { mutableStateOf(ColorMode.SYSTEM) }

    val appSettings = AppSettings(
        colorMode = colorMode,
        keyColor = 0,
        paletteStyle = PaletteStyle.TonalSpot,
        colorSpec = ColorSpec.SpecVersion.SPEC_2025,
    )

    CompositionLocalProvider(LocalUiMode provides uiMode) {
        HpPrintTheme(appSettings = appSettings, uiMode = uiMode) {
            val scope = rememberCoroutineScope()
            val pagerState = rememberPagerState(pageCount = { 2 })

            Box(Modifier.fillMaxSize().background(setupSurface())) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                ) { page ->
                    when (page) {
                        0 -> WelcomePage(onNext = { scope.launch { pagerState.animateScrollToPage(1) } })
                        1 -> ConfigPage(
                            uiMode = uiMode,
                            colorMode = colorMode,
                            onUiModeChange = { uiMode = it },
                            onColorModeChange = { colorMode = it },
                            onDone = {
                                val repo = SettingsRepositoryImpl()
                                repo.uiMode = uiMode.value
                                repo.themeMode = colorMode.value
                                ProvisionManager.setProvisioned(true)
                                onComplete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun setupSurface(): Color =
    if (LocalUiMode.current == UiMode.Material) MaterialTheme.colorScheme.surface
    else MiuixTheme.colorScheme.surface

@Composable
private fun setupOnBackground(): Color =
    if (LocalUiMode.current == UiMode.Material) MaterialTheme.colorScheme.onBackground
    else MiuixTheme.colorScheme.onBackground

@Composable
private fun setupPrimary(): Color =
    if (LocalUiMode.current == UiMode.Material) MaterialTheme.colorScheme.primary
    else MiuixTheme.colorScheme.primary

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val onBackground = setupOnBackground()
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entered = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
    )

    val content = @Composable {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(Modifier.scale(logoScale))
                Spacer(Modifier.height(28.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(contentAlpha),
                ) {
                    Text(
                        text = stringResource(R.string.hp_setup_welcome_title),
                        color = onBackground,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.hp_setup_welcome_subtitle),
                        color = onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PrimaryButton(
                text = stringResource(R.string.hp_setup_get_started),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 40.dp, vertical = 48.dp)
                    .fillMaxWidth()
                    .alpha(contentAlpha),
                onClick = onNext,
            )
        }
    }

    if (LocalUiMode.current == UiMode.Miuix) {
        // 参照「关于页」：用同一套动态背景光效（BgEffectBackground），
        // 引导页直接播放动态动画（不依赖模糊开关，只要设备支持着色器）。
        val backdrop = rememberLayerBackdrop()
        val shaderSupported = remember { isRuntimeShaderSupported() }
        BgEffectBackground(
            dynamicBackground = shaderSupported,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            isFullSize = true,
            effectBackground = shaderSupported,
            alpha = { 1f },
        ) {
            content()
        }
    } else {
        Box(Modifier.fillMaxSize().background(setupSurface())) {
            content()
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    if (LocalUiMode.current == UiMode.Miuix) {
        // 参照「关于页」的 Logo：前景图标着色主题色，并用液态玻璃色彩混合光效。
        val colorScheme = MiuixTheme.colorScheme
        val isInDark = isInDarkTheme()
        val shaderSupported = remember { isRuntimeShaderSupported() }
        val backdrop = rememberLayerBackdrop()
        val logoBlend = remember(isInDark) {
            if (isInDark) {
                listOf(
                    BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                    BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
                )
            } else {
                listOf(
                    BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                    BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(100.dp)
                .clipToBounds(),
        ) {
            Image(
                modifier = Modifier
                    .requiredSize(245.dp)
                    .then(
                        if (shaderSupported) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(0.dp),
                                blurRadius = 150f,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = ComposeBlendMode.DstIn,
                                enabled = true,
                            )
                        } else Modifier
                    ),
                painter = painterResource(R.drawable.ic_launcher_foreground),
                colorFilter = ColorFilter.tint(colorScheme.onBackground),
                contentDescription = null,
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BrandGreen),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (LocalUiMode.current == UiMode.Material) {
        MaterialButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            MiuixText(
                text = text,
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun ConfigPage(
    uiMode: UiMode,
    colorMode: ColorMode,
    onUiModeChange: (UiMode) -> Unit,
    onColorModeChange: (ColorMode) -> Unit,
    onDone: () -> Unit,
) {
    val onBackground = setupOnBackground()
    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.hp_setup_ui_style),
                color = onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(onBackground.copy(alpha = 0.08f))
                    .padding(4.dp),
            ) {
                SegmentedOption(
                    label = "Miuix",
                    selected = uiMode == UiMode.Miuix,
                    onBackground = onBackground,
                    onClick = { onUiModeChange(UiMode.Miuix) },
                    modifier = Modifier.weight(1f),
                )
                SegmentedOption(
                    label = "Material",
                    selected = uiMode == UiMode.Material,
                    onBackground = onBackground,
                    onClick = { onUiModeChange(UiMode.Material) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.hp_setup_theme),
                color = onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(onBackground.copy(alpha = 0.08f))
                    .padding(4.dp),
            ) {
                SegmentedOption(
                    label = stringResource(R.string.settings_theme_mode_system),
                    selected = colorMode == ColorMode.SYSTEM,
                    onBackground = onBackground,
                    onClick = { onColorModeChange(ColorMode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                SegmentedOption(
                    label = stringResource(R.string.settings_theme_mode_light),
                    selected = colorMode == ColorMode.LIGHT,
                    onBackground = onBackground,
                    onClick = { onColorModeChange(ColorMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                SegmentedOption(
                    label = stringResource(R.string.settings_theme_mode_dark),
                    selected = colorMode == ColorMode.DARK,
                    onBackground = onBackground,
                    onClick = { onColorModeChange(ColorMode.DARK) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(48.dp))

            PrimaryButton(
                text = stringResource(R.string.hp_setup_done),
                modifier = Modifier.fillMaxWidth(),
                onClick = onDone,
            )
        }
    }

    if (LocalUiMode.current == UiMode.Miuix) {
        // 与「欢迎页 / 关于页」一致：第二页同样走动态背景光效，统一主题风格。
        val backdrop = rememberLayerBackdrop()
        val shaderSupported = remember { isRuntimeShaderSupported() }
        BgEffectBackground(
            dynamicBackground = shaderSupported,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            isFullSize = true,
            effectBackground = shaderSupported,
            alpha = { 1f },
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = setupPrimary()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else onBackground,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}