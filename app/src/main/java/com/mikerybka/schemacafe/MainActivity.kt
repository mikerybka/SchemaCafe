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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

    var schemaIDsJSON by remember { mutableStateOf("[]") }

    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newSchemaName by remember { mutableStateOf("") }
    val newSchemaID = newSchemaName
        .lowercase()                       // 1. make lowercase
        .replace(Regex("[^a-z0-9\\s]"), "") // 2. remove non-alphanumeric (except spaces)
        .trim()                             // 3. remove leading/trailing spaces
        .replace(Regex("\\s+"), "-")        // 4. replace 1+ spaces with one dash
    var addSchemaError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = dataStore.data.first()
        savedToken = prefs[stringPreferencesKey("github_token")]
        if (savedToken != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/mikerybka/data/contents/schemas")
                    .header("Authorization", "token $savedToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // TODO let the user know the request failed
                        println("Request failed: ${response.code}")
                        return@use
                    }
                    schemaIDsJSON = response.body?.string() ?: "[]"
                }
            }
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...")
        }
    } else if (savedToken == null) {
        return Scaffold(
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
                    Column {
                        OutlinedTextField(
                            value = inputToken,
                            onValueChange = { inputToken = it },
                            label = { Text("GitHub API Key") }
                        )
                        BasicText(
                            text = buildAnnotatedString {
                                withLink(
                                    LinkAnnotation.Url(
                                        url = "https://github.com/settings/personal-access-tokens/new",
                                    )
                                ) {
                                    withStyle(
                                        style = SpanStyle(
                                            color = Color.Blue,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    ) {
                                        append("Create a new access token")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                }
            }
        }
    } else {
        val schemaIDs = mutableListOf<String>()
        val arr = JSONArray(schemaIDsJSON)
        for (i in 0 until arr.length()) {
            val file = arr.getJSONObject(i)
            schemaIDs.add(file.getString("name").removeSuffix(".json"))
        }
        return ModalNavigationDrawer(
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
            NavHost(
                navController = navController,
                startDestination = "schemas",
                modifier =  Modifier.fillMaxSize()
            ) {
                composable("schemas") {
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
                                                if (!response.isSuccessful) {
                                                    // TODO let the user know the request failed
                                                    println("Request failed: ${response.code}")
                                                    return@use
                                                }
                                                schemaIDsJSON = response.body?.string() ?: "[]"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = {
                                    showAddSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add"
                                )
                            }
                        }
                    ) { innerPadding ->
                        LazyColumn(
                            modifier = Modifier.padding(innerPadding),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(schemaIDs) { id ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            navController.navigate("schemas/test")
                                        }
                                        .padding(16.dp)
                                ) {
                                    Text(id)
                                }
                            }
                        }

                        if (showAddSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showAddSheet = false },
                                sheetState = addSheetState
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Add Schema", style = MaterialTheme.typography.titleMedium)
                                    Text("ID: $newSchemaID")
                                    TextField(
                                        value = newSchemaName,
                                        onValueChange = {
                                            newSchemaName = it
                                            addSchemaError = null
                                        },
                                        label = { Text("Name") },
                                        isError = addSchemaError != null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (addSchemaError != null) {
                                        Text(
                                            text = addSchemaError ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(onClick = {
                                            newSchemaName = ""
                                            showAddSheet = false
                                        }) {
                                            Text("Cancel")
                                        }
                                        TextButton(onClick = {

                                            when {
                                                newSchemaID.isBlank() ->
                                                    addSchemaError = "ID cannot be empty"
                                                schemaIDs.contains(newSchemaID) ->
                                                    addSchemaError = "ID already in use"
                                                else -> {
                                                    CoroutineScope(Dispatchers.IO).launch {

                                                    }
                                                    newSchemaName = ""
                                                    addSchemaError = null
                                                    showAddSheet = false
                                                }
                                            }
                                        }) {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                composable(
                    route = "schemas/{schemaID}",
                    arguments = listOf(navArgument("schemaID") { type = NavType.StringType })
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Detail") },
                                navigationIcon = {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        Box(Modifier.padding(innerPadding), contentAlignment = Alignment.Center) {
                            Text("Schema View")
                        }
                    }
                }
            }
        }
    }
}

