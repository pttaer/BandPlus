package vn.bandplus.setup

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.key
import vn.bandplus.ui.Footnote
import vn.bandplus.ui.Good
import vn.bandplus.ui.Bad
import vn.bandplus.ui.SectionTitle

fun isIgnoringBatteryOptimizations(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 23 || ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

fun hasNotificationAccess(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun start(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
}

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

/** Route "setup/reliability". Shown once after first launch, reachable from Band screen. */
@SuppressLint("BatteryLife")
@Composable
fun ReliabilityScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    SectionTitle("Giữ BandPlus chạy qua đêm")
    key(tick) { Checks(ctx) }
    OutlinedButton(onClick = { tick++ }) { Text("Kiểm tra lại") }
    Footnote("Bật “Tự động đồng bộ” trong Cài đặt vòng để lấy dữ liệu định kỳ.")
}

@SuppressLint("BatteryLife")
@Composable
private fun Checks(ctx: Context) {
    Text("vivo/Android có thể tắt ứng dụng nền. Nếu bị tắt, giấc ngủ vẫn lưu trên vòng và được lấy vào sáng hôm sau.", color = Color.White, fontSize = 14.sp)

    Check("Bỏ tối ưu pin", isIgnoringBatteryOptimizations(ctx)) {
        start(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
    Check("Quyền đọc thông báo", hasNotificationAccess(ctx)) {
        start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    Check("Tự khởi động (vivo)", null) {
        start(ctx,
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            appDetails(ctx))
    }
}

@Composable
private fun Check(label: String, ok: Boolean?, fix: () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Text(
        (when (ok) { true -> "✓ "; false -> "✗ "; null -> "? " }) + label,
        color = when (ok) { true -> Good; false -> Bad; null -> Color.White },
        modifier = Modifier.weight(1f).padding(top = 12.dp)
    )
    if (ok != true) OutlinedButton(onClick = fix) { Text("Sửa") }
}
