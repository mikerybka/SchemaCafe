package com.mikerybka.schemacafe

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mikerybka.schemacafe.ui.theme.SchemaCafeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {

    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf((true)) }
    var savedToken by remember { mutableStateOf<String?>(null) }
    var inputToken by remember { mutableStateOf("") }

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var schemaIDs = remember { mutableStateListOf<String>()}

    LaunchedEffect(Unit) {
        val prefs = dataStore.data.first()
        savedToken = prefs[stringPreferencesKey("github_token")]
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...")
        }
    } else if (savedToken == null) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            floatingActionButton = {
                Button(onClick = {
                    scope.launch {
                        dataStore.edit { prefs ->
                            prefs[stringPreferencesKey("github_token")] = inputToken
                        }
                        savedToken = inputToken
                        inputToken = ""
                    }
                }) {
                    Text("Submit")
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
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
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "Settings",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    NavigationDrawerItem(
                        label = { Text("Logout")},
                        selected = false,
                        onClick = {
                            scope.launch {
                                dataStore.edit { prefs ->
                                    prefs.remove(stringPreferencesKey("github_token"))
                                }
                                drawerState.close()
                                savedToken = null
                            }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Schemas") },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Settings")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val request = Request.Builder()
                                        .url("https://api.github.com/repos/mikerybka/data/contents/schemas")
                                        .header("Authorization", "token $savedToken")
                                        .header("Accept", "application/vnd.github.v3+json")
                                        .build()
                                    OkHttpClient().newCall(request).execute().use { response ->
                                        val body = response.body?.string() ?: ""
                                        if (!response.isSuccessful) {
                                            println("Request failed: ${response.code}")
                                            println(body)
                                            return@use
                                        }
                                        val jsonArray = JSONArray(body)
                                        for (i in 0 until jsonArray.length()) {
                                            val file = jsonArray.getJSONObject(i)
                                            println(file.getString("name") + " (${file.getString("type")})")
                                            schemaIDs.add(file.getString("name"))
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "schemas",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("schemas") {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(schemaIDs) { id ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(id)
                                }
                            }
                        }
                    }
                    composable(
                        route = "schemas/{schemaID}",
                        arguments = listOf(navArgument("schemaID") { type = NavType.StringType })
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Schema View")
                        }
                    }
                }
            }
        }
    }
}

