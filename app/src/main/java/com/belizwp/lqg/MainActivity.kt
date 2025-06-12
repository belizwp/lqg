package com.belizwp.lqg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.belizwp.lqg.ui.theme.LqgTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LqgTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val time = produceState(0f) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                value = it / 1000f
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.liquidGlass(hazeState, time::value),
                title = {
                    Text("Liquid Glass")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavBar(hazeState = hazeState, time = time::value)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .hazeSource(hazeState, zIndex = 1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Image(
                painter = painterResource(R.drawable.sample_bg),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = 0f),
                contentScale = ContentScale.Crop,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(20) { index ->
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            )
                            .liquidGlass(hazeState, time::value)
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Item $index")
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    time: () -> Float = { 0f },
) {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .liquidGlass(hazeState, time),
        )
        NavigationBar(
            modifier = modifier,
            containerColor = Color.Transparent,
        ) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                    )
                },
                label = {
                    Text("Home")
                },
            )
            NavigationBarItem(
                selected = false,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                    )
                },
                label = {
                    Text("Account")
                },
            )
        }
    }
}

@Preview
@Composable
private fun MainScreenPreview() = LqgTheme(darkTheme = true) {
    MainScreen()
}