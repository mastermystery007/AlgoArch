package com.algoarch.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.algoarch.app.AlgoArchApp
import com.algoarch.app.ui.game.GameScreen
import com.algoarch.app.ui.home.HomeScreen
import com.algoarch.app.ui.profile.ProfileScreen
import com.algoarch.app.ui.theme.AA

@Composable
fun AlgoArchNav(app: AlgoArchApp) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "tabs") {
        composable("tabs") {
            MainTabs(
                app = app,
                onPlay = { levelId -> nav.navigate("play/$levelId") },
                onReview = { nav.navigate("review") },
            )
        }
        composable("play/{levelId}") { entry ->
            GameScreen(
                app = app,
                levelId = entry.arguments?.getString("levelId"),
                onExit = { nav.popBackStack() },
                onReview = { nav.navigate("review") { popUpTo("tabs") } },
            )
        }
        composable("review") {
            GameScreen(
                app = app,
                levelId = null,
                onExit = { nav.popBackStack() },
                onReview = {},
            )
        }
    }
}

@Composable
private fun MainTabs(app: AlgoArchApp, onPlay: (String) -> Unit, onReview: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = AA.Ink,
        bottomBar = {
            NavigationBar(containerColor = AA.Panel) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AA.Ink,
                    selectedTextColor = AA.Text,
                    indicatorColor = AA.Algo,
                    unselectedIconColor = AA.Muted,
                    unselectedTextColor = AA.Muted,
                )
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Map") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(app, Modifier.padding(padding), onPlay = onPlay, onReview = onReview)
            else -> ProfileScreen(app, Modifier.padding(padding))
        }
    }
}
