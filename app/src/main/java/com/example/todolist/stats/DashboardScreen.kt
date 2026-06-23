package com.example.todolist.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val stats: DashboardStats? = null,
    val isLoading: Boolean = true,
    val selectedPeriod: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val statsCalculator: StatsCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadStats()
    }

    fun loadStats(days: Int = _uiState.value.selectedPeriod) {
        _uiState.value = _uiState.value.copy(isLoading = true, selectedPeriod = days)
        viewModelScope.launch {
            val stats = statsCalculator.compute(if (days == 0) 0 else days)
            _uiState.value = _uiState.value.copy(stats = stats, isLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val periods = listOf(0 to "Todos", 7 to "7d", 30 to "30d", 60 to "60d", 90 to "90d")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meu Progresso") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            uiState.stats?.let { stats ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    PeriodSelector(
                        periods = periods,
                        selected = uiState.selectedPeriod,
                        onSelect = { viewModel.loadStats(it) }
                    )

                    Spacer(Modifier.height(16.dp))

                    StatsCards(stats)

                    Spacer(Modifier.height(24.dp))

                    if (uiState.selectedPeriod > 0) {
                        Text("Conclusões por Dia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        BarChart(stats.dailyCompletions)
                        Spacer(Modifier.height(24.dp))
                    }

                    Text("Desempenho por Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    CategoryBars(stats.categoryStats)

                    Spacer(Modifier.height(24.dp))

                    Text("Distribuição Geral", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    PieChart(stats.completedTasks.toFloat(), stats.pendingTasks.toFloat())
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun StatsCards(stats: DashboardStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("Total", stats.totalTasks.toString(), Color(0xFF607D8B))
        StatCard("Feitas", stats.completedTasks.toString(), Color(0xFF4CAF50))
        StatCard("Pendentes", stats.pendingTasks.toString(), Color(0xFFFFC107))
        StatCard("Atrasadas", stats.overdueTasks.toString(), Color(0xFFF44336))
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("Taxa", "${(stats.completionRate * 100).toInt()}%", MaterialTheme.colorScheme.primary)
        StatCard("Recorrentes", stats.recurringActive.toString(), Color(0xFF9C27B0))
        StatCard("Com Prazo", stats.tasksWithDeadline.toString(), Color(0xFF00BCD4))
        StatCard("Categorias", stats.categoryStats.size.toString(), Color(0xFFFF5722))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color) {
    Card(
        //modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChart(data: List<DailyCompletion>) {
    val maxVal = data.maxOfOrNull { it.count } ?: 1
    val barCount = data.size
    Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val barWidth = size.width / barCount * 0.7f
            val gap = size.width / barCount * 0.3f
            data.forEachIndexed { i, item ->
                val barHeight = (item.count.toFloat() / maxVal) * (size.height - 30f)
                val x = i * (barWidth + gap) + gap / 2
                val y = size.height - barHeight - 25f
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    alpha = 0.8f
                )
            }
            data.forEachIndexed { i, item ->
                val x = i * (barWidth + gap) + gap / 2 + barWidth / 2 - 15f
                drawContext.canvas.nativeCanvas.drawText(
                    item.day, x, size.height - 5f,
                    android.graphics.Paint().apply {
                        textSize = 20f; textAlign = android.graphics.Paint.Align.LEFT; color = android.graphics.Color.GRAY
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryBars(categories: List<CategoryStats>) {
    val animProgress = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000)
    ).value

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (categories.isEmpty()) {
            Text("Nenhuma categoria", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        categories.forEach { cat ->
            val color = when {
                cat.rate >= 0.8f -> Color(0xFF4CAF50)
                cat.rate >= 0.5f -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                    Text("${cat.completed}/${cat.total}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(6.dp))) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = cat.rate * animProgress)
                            .background(color, RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun PieChart(completed: Float, pending: Float) {
    val total = completed + pending
    if (total == 0f) {
        Text("Nenhuma tarefa", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val completedAngle = completed / total * 360f
    val pendingAngle = pending / total * 360f

    val animCompleted = animateFloatAsState(targetValue = completedAngle, animationSpec = tween(1200)).value
    val animPending = animateFloatAsState(targetValue = pendingAngle, animationSpec = tween(1200)).value

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeWidth = 40f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = Color(0xFF4CAF50),
                startAngle = -90f,
                sweepAngle = animCompleted,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            drawArc(
                color = Color(0xFFFFC107),
                startAngle = -90f + animCompleted,
                sweepAngle = animPending,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFF4CAF50)))
                Spacer(Modifier.width(6.dp))
                Text("${(completed / total * 100).toInt()}% Concluídas")
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFFFFC107)))
                Spacer(Modifier.width(6.dp))
                Text("${(pending / total * 100).toInt()}% Pendentes")
            }
        }
    }

    if (completed > 0 && pending > 0) {
        Spacer(Modifier.height(12.dp))
        val best = if (completed >= pending) "Concluídas" else "Pendentes"
        Text(
            "Maior parte: $best",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
