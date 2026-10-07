package com.watchalarm.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Begründung für den Zugriff auf Schlafdaten.
 *
 * Health Connect verlangt diese Activity: Sie öffnet sich über den Link
 * „Datenschutzerklärung" im Berechtigungsdialog (bis Android 13 über
 * `ACTION_SHOW_PERMISSIONS_RATIONALE`, ab Android 14 über den Alias
 * `ViewPermissionUsageActivity` im Manifest). Ohne sie lehnt Health Connect
 * die Anfrage ab. Sie muss auf dieselbe Datenschutzerklärung verweisen wie
 * der Play-Store-Eintrag.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.health_rationale_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.health_explanation), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.health_rationale_details), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                                }
                            },
                        ) {
                            Text(stringResource(R.string.health_privacy_policy))
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** Dieselbe Adresse wie in der Play Console (siehe RELEASING.md). */
        const val PRIVACY_POLICY_URL = "https://schubbii.github.io/WatchAlarm/privacy/"
    }
}
