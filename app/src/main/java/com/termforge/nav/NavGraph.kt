package com.termforge.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.termforge.terminal.TerminalScreen
import com.termforge.ui.active.ActiveSessionsScreen
import com.termforge.ui.hostedit.HostEditorScreen
import com.termforge.ui.hosts.HostListScreen
import com.termforge.ui.identities.IdentityScreen
import com.termforge.ui.keybar.KeyBarEditorScreen
import com.termforge.ui.settings.SettingsScreen
import com.termforge.ui.sftp.SftpScreen
import com.termforge.ui.snippets.SnippetsScreen

/** The app's single navigation graph with Material-motion slide transitions between routes. */
@Composable
fun TermForgeNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOSTS,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) },
    ) {
        composable(Routes.HOSTS) { HostListScreen(navController) }
        composable(Routes.IDENTITIES) { IdentityScreen(navController) }
        composable(Routes.SNIPPETS) { SnippetsScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.ACTIVE_SESSIONS) { ActiveSessionsScreen(navController) }

        composable(
            route = Routes.HostEdit.PATTERN,
            arguments = listOf(navArgument(Routes.HostEdit.ARG_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            }),
        ) { HostEditorScreen(navController) }

        composable(
            route = Routes.Terminal.PATTERN,
            arguments = listOf(
                navArgument(Routes.Terminal.ARG_SESSION) { type = NavType.StringType },
                navArgument(Routes.Terminal.ARG_HOST) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { TerminalScreen(navController) }

        composable(
            route = Routes.Sftp.PATTERN,
            arguments = listOf(navArgument(Routes.Sftp.ARG_HOST) { type = NavType.LongType }),
        ) { SftpScreen(navController) }

        composable(
            route = Routes.KeyBarEditor.PATTERN,
            arguments = listOf(
                navArgument(Routes.KeyBarEditor.ARG_SCOPE) { type = NavType.StringType; defaultValue = "GLOBAL" },
                navArgument(Routes.KeyBarEditor.ARG_HOST_ID) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { KeyBarEditorScreen(navController) }
    }
}
