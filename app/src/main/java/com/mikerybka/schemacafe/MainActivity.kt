package com.mikerybka.schemacafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.mikerybka.schemacafe.ui.theme.SchemaCafeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

private val ComponentActivity.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SchemaCafeTheme {
                App(dataStore = dataStore)
            }
        }
    }
}

@Composable
fun App(dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
    var githubTokenKey = stringPreferencesKey("github_token")
    val scope = rememberCoroutineScope()
    var savedToken by remember { mutableStateOf<String?>(null) }
    var inputToken by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val prefs = dataStore.data.first()
        savedToken = prefs[githubTokenKey]
    }

    if (savedToken != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Saved value: ${savedToken!!}")
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            floatingActionButton = {
                Button(onClick = {
                    scope.launch {
                        dataStore.edit { prefs ->
                            prefs[githubTokenKey] = inputToken
                        }
                        savedToken = inputToken
                    }
                }) {
                    Text("Submit")
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("GitHub API Key") }
                    )
                }
            }
        }
    }
}
