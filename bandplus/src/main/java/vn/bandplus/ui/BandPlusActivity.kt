package vn.bandplus.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vn.bandplus.data.BandPlus
import vn.bandplus.setup.ReliabilityScreen

class BandPlusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bandplus", Context.MODE_PRIVATE)
        val firstRun = !prefs.getBoolean("reliability_shown", false)
        if (firstRun) prefs.edit().putBoolean("reliability_shown", true).apply()
        vn.bandplus.work.scheduleWork(this)
        setContent { BandPlusTheme { App(if (firstRun) "reliability" else "home") } }
    }
}

private val tabs = listOf("home" to "Hôm nay", "sleep" to "Ngủ", "heart" to "Tim", "vitals" to "SpO2", "band" to "Vòng")

@Composable
fun App(start: String) {
    var route by rememberSaveable { mutableStateOf(start) }
    val src = BandPlus.source
    BackHandler(enabled = route != "home") { route = "home" }
    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                tabs.forEach { (r, label) ->
                    NavigationBarItem(selected = route == r, onClick = { route = r }, icon = { Text(label.take(1)) }, label = { Text(label) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            if (src == null) {
                Text("Chưa có kết nối tới dữ liệu Gadgetbridge.", color = Bad)
                return@Column
            }
            when (route) {
                "sleep" -> SleepScreen(src)
                "heart" -> HeartScreen(src)
                "vitals" -> VitalsScreen(src)
                "band" -> BandScreen(src) { route = it }
                "reports" -> ReportsScreen(src)
                "reliability" -> ReliabilityScreen()
                else -> DashboardScreen(src) { route = it }
            }
        }
    }
}

@Composable
fun BandPlusTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Cyan, background = Color.Black, surface = CardBg,
        onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White,
    ),
    content = content,
)
