package com.azz.colorskip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azz.colorskip.core.ClickMethod
import com.azz.colorskip.core.RuleSource
import com.azz.colorskip.core.SkipLog
import com.azz.colorskip.core.SkipRule
import com.azz.colorskip.ui.theme.Amber
import com.azz.colorskip.ui.theme.Danger
import com.azz.colorskip.ui.theme.Ink
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.Mint
import com.azz.colorskip.ui.theme.Outline
import com.azz.colorskip.ui.theme.SurfaceHigh
import com.azz.colorskip.ui.theme.TextMain
import com.azz.colorskip.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Lime,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextMain)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
        if (action != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Lime)
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Lime,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    color: Color = Lime,
    background: Color = color.copy(alpha = 0.12f),
    modifier: Modifier = Modifier,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(100),
        modifier = modifier,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (active) Lime else Danger),
    )
}

@Composable
fun StatusTile(
    title: String,
    value: String,
    active: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = SurfaceHigh,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active)
                Spacer(Modifier.width(6.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) TextMain else Danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    icon: ImageVector = Icons.Default.Warning,
    accent: Color = Mint,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceHigh,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(11.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            if (action != null) {
                Spacer(Modifier.height(14.dp))
                action()
            }
        }
    }
}

@Composable
fun AppBadge(label: String, modifier: Modifier = Modifier) {
    val color = when ((label.hashCode() and 0x7fffffff) % 3) {
        0 -> Lime
        1 -> Mint
        else -> Amber
    }
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

@Composable
fun LogRow(log: SkipLog, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (compact) Color.Transparent else SurfaceHigh.copy(alpha = 0.65f))
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppBadge(log.appLabel.take(1).ifBlank { "?" })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    log.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatLogTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                log.nodeText.ifBlank { log.ruleName },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain.copy(alpha = 0.9f),
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(log.method.label, methodColor(log.method))
                Spacer(Modifier.width(7.dp))
                Text(
                    log.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun RuleRow(rule: SkipRule, onDelete: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill(
                    when (rule.source) {
                        RuleSource.BUILTIN -> "内置"
                        RuleSource.IMPORTED -> "导入"
                        RuleSource.USER -> "自定义"
                    },
                    if (rule.source == RuleSource.BUILTIN) TextMuted else Mint,
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除规则",
                            tint = Danger.copy(alpha = 0.8f),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (rule.packages.isEmpty()) "所有应用" else rule.packages.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.isCoordinate) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "坐标 ${(rule.xPercent ?: 0f).times(100).toInt()}% / " +
                        "${(rule.yPercent ?: 0f).times(100).toInt()}% · 延迟 ${rule.delayMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            } else if (rule.keywords.isNotEmpty() || rule.viewIds.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    buildList {
                        addAll(rule.keywords.take(4))
                        rule.viewIds.take(3).forEach { add("id:$it") }
                    }.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Lime.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Default.CheckCircle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Lime.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Lime.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

@Composable
fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Mint),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "无 INTERNET 权限 · 规则与日志只存本机",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
fun StepNumber(number: Int, active: Boolean = false) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (active) Lime.copy(alpha = 0.16f) else SurfaceHigh)
            .border(1.dp, if (active) Lime.copy(alpha = 0.4f) else Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Lime else TextMuted,
        )
    }
}

fun formatLogTime(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(timestamp).atZone(zone)
    val now = LocalDate.now(zone)
    return when {
        time.toLocalDate() == now -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
        time.toLocalDate() == now.minusDays(1) -> "昨天 " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
}

fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "还没有记录"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> formatLogTime(timestamp)
    }
}

fun methodColor(method: ClickMethod): Color = when (method) {
    ClickMethod.NODE_CLICK -> Lime
    ClickMethod.GESTURE -> Mint
    ClickMethod.COORDINATE -> Amber
}
