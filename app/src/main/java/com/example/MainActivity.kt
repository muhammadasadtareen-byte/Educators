package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.SchoolRepository
import com.example.data.model.StaffRole
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClassResultsScreen
import com.example.ui.screens.ClassesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExcelCenterScreen
import com.example.ui.screens.MarksEntryScreen
import com.example.ui.screens.PdfCenterScreen
import com.example.ui.screens.ResultCardsScreen
import com.example.ui.screens.SchoolLogoBadge
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusPill
import com.example.ui.screens.StudentsScreen
import com.example.ui.screens.SubjectsScreen
import com.example.ui.screens.TeacherActivityScreen
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EducatorsTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object AppRoute {
    const val DASHBOARD = "dashboard"
    const val STUDENTS = "students"
    const val CLASSES = "classes"
    const val SUBJECTS = "subjects"
    const val MARKS_ENTRY = "marks_entry"
    const val RESULT_CARDS = "result_cards"
    const val CLASS_RESULTS = "class_results"
    const val EXCEL_CENTER = "excel_export"
    const val PDF_CENTER = "pdf_export"
    const val TEACHER_ACTIVITY = "teacher_activity"
    const val SETTINGS = "settings"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false
)

private val ALL_DESTINATIONS = listOf(
    NavDestination(AppRoute.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    NavDestination(AppRoute.STUDENTS, "Student Management", Icons.Default.People, adminOnly = true),
    NavDestination(AppRoute.CLASSES, "Class Management", Icons.Default.Class, adminOnly = true),
    NavDestination(AppRoute.SUBJECTS, "Subject Management", Icons.Default.MenuBook, adminOnly = true),
    NavDestination(AppRoute.MARKS_ENTRY, "Marks Entry", Icons.Default.EditNote),
    NavDestination(AppRoute.RESULT_CARDS, "Result Cards", Icons.Default.Badge),
    NavDestination(AppRoute.CLASS_RESULTS, "Class Results", Icons.Default.Assessment),
    NavDestination(AppRoute.EXCEL_CENTER, "Excel Export", Icons.Default.TableChart),
    NavDestination(AppRoute.PDF_CENTER, "PDF Export", Icons.Default.PictureAsPdf),
    NavDestination(AppRoute.TEACHER_ACTIVITY, "Teacher Activity", Icons.Default.History, adminOnly = true),
    NavDestination(AppRoute.SETTINGS, "Settings", Icons.Default.Settings)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EducatorsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedSchoolApp(currentUserId = user.uid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedSchoolApp(currentUserId: String) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(SchoolRepository(db), currentUserId)
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    when (val state = uiState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EducatorsNavy)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading campus records...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Unable to Load Campus", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.initializeWorkspace() }) {
                        Text("Retry")
                    }
                }
            }
        }
        is UiState.Success -> {
            MainAdaptiveShell(
                snapshot = state.data,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAdaptiveShell(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.DASHBOARD
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val availableDestinations = remember(snapshot.effectiveRole, snapshot.isOwnerOrAdmin) {
        ALL_DESTINATIONS.filter { dest ->
            !dest.adminOnly || snapshot.effectiveRole == StaffRole.ADMIN
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentTitle = if (currentRoute == AppRoute.SETTINGS && snapshot.effectiveRole == StaffRole.TEACHER) {
        "Teacher Account"
    } else {
        ALL_DESTINATIONS.firstOrNull { it.route == currentRoute }?.label ?: "Educators Results"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(260.dp)) {
                        DrawerContentBody(
                            snapshot = snapshot,
                            destinations = availableDestinations,
                            currentRoute = currentRoute,
                            onSelectRoute = { navigateTo(it) }
                        )
                    }
                }
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = EducatorsNavy,
                                titleContentColor = Color.White
                            )
                        )
                    }
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        snapshot = snapshot,
                        viewModel = viewModel,
                        onNavigate = { navigateTo(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        DrawerContentBody(
                            snapshot = snapshot,
                            destinations = availableDestinations,
                            currentRoute = currentRoute,
                            onSelectRoute = {
                                scope.launch { drawerState.close() }
                                navigateTo(it)
                            }
                        )
                    }
                }
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(currentTitle, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = EducatorsNavy,
                                titleContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        val bottomItems = listOf(
                            NavDestination(AppRoute.DASHBOARD, "Home", Icons.Default.Dashboard),
                            NavDestination(AppRoute.MARKS_ENTRY, "Marks", Icons.Default.EditNote),
                            NavDestination(AppRoute.RESULT_CARDS, "Cards", Icons.Default.Badge),
                            NavDestination(AppRoute.CLASS_RESULTS, "Sheet", Icons.Default.Assessment)
                        )
                        NavigationBar {
                            bottomItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        snapshot = snapshot,
                        viewModel = viewModel,
                        onNavigate = { navigateTo(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContentBody(
    snapshot: SchoolSnapshot,
    destinations: List<NavDestination>,
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SchoolLogoBadge(
                logoBase64 = snapshot.campus.logoBase64,
                sizeDp = 42,
                paddingDp = 3
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.campus.schoolName,
                    style = MaterialTheme.typography.titleMedium,
                    color = EducatorsNavy
                )
                Text(
                    text = snapshot.campus.campusName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = "Role: ${snapshot.effectiveRole.name}",
                isPositive = snapshot.effectiveRole == StaffRole.ADMIN,
                isWarning = snapshot.effectiveRole == StaffRole.TEACHER
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        destinations.forEach { dest ->
            val labelText = if (dest.route == AppRoute.SETTINGS && snapshot.effectiveRole == StaffRole.TEACHER) {
                "Teacher Account"
            } else {
                dest.label
            }
            NavigationDrawerItem(
                label = { Text(labelText) },
                selected = currentRoute == dest.route,
                onClick = { onSelectRoute(dest.route) },
                icon = { Icon(dest.icon, contentDescription = labelText) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.DASHBOARD,
        modifier = modifier.fillMaxSize()
    ) {
        composable(AppRoute.DASHBOARD) {
            DashboardScreen(
                snapshot = snapshot,
                currentUserId = viewModel.currentUserId,
                onSelectSession = { viewModel.selectSession(it) },
                onSelectExam = { viewModel.selectExam(it) },
                onNavigate = onNavigate,
                onSwitchCampus = { campusId, label ->
                    viewModel.switchCampus(campusId, label)
                },
                onUpdateExam = { examId, sessionId, name, examType, useCustomGrading, sortOrder, components ->
                    viewModel.updateExamination(
                        campusId = snapshot.campus.id,
                        examId = examId,
                        sessionId = sessionId,
                        name = name,
                        examType = examType,
                        useCustomGrading = useCustomGrading,
                        sortOrder = sortOrder,
                        components = components
                    )
                },
                onCreateExam = { sessionId, name, examType, useCustomGrading, sortOrder, components ->
                    viewModel.addExamination(
                        campus = snapshot.campus,
                        sessionId = sessionId,
                        name = name,
                        examType = examType,
                        useCustomGrading = useCustomGrading,
                        sortOrder = sortOrder,
                        components = components
                    )
                },
                onDeleteExam = { examId ->
                    viewModel.deleteExamination(snapshot.campus.id, examId)
                }
            )
        }
        composable(AppRoute.STUDENTS) {
            StudentsScreen(snapshot = snapshot, viewModel = viewModel, onNavigate = onNavigate)
        }
        composable(AppRoute.CLASSES) {
            ClassesScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.SUBJECTS) {
            SubjectsScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.MARKS_ENTRY) {
            MarksEntryScreen(
                snapshot = snapshot,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.RESULT_CARDS) {
            ResultCardsScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.CLASS_RESULTS) {
            ClassResultsScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.EXCEL_CENTER) {
            ExcelCenterScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.PDF_CENTER) {
            PdfCenterScreen(snapshot = snapshot, viewModel = viewModel)
        }
        composable(AppRoute.TEACHER_ACTIVITY) {
            TeacherActivityScreen(snapshot = snapshot)
        }
        composable(AppRoute.SETTINGS) {
            SettingsScreen(snapshot = snapshot, viewModel = viewModel)
        }
    }
}
