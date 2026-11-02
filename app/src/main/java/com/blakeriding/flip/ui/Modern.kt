package com.blakeriding.flip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** V3 colours and layout on a normal touch home screen: clock, 9 favourites, keypad, search. */
@Composable
fun ModernHome(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    val keypad = ui.keypadOn(nav.screen)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06142E), Color(0xFF0B2A5C), Color(0xFF0B1220))))
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ui.timeText(), fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color.White)
            if (ui.amPm().isNotEmpty()) Text(" " + ui.amPm(), fontSize = 18.sp, color = Color(0xFF9CC9FF), modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(ui.dateText(), color = Color(0xFF9CC9FF))
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                nav.digits.isNotEmpty() && ui.searching(nav.screen) -> ModernResults(ui, nav, actions)
                nav.digits.isNotEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(nav.digits, fontSize = 40.sp, color = Color.White)
                }
                else -> FavoritesGrid(ui, nav, actions)
            }
        }

        if (keypad) {
            Spacer(Modifier.height(8.dp))
            ModernKeypad(nav, actions)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (nav.screen == Screen.SEARCH) {
                IconButton(onClick = { nav.home() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            } else if (!keypad) {
                IconButton(onClick = { nav.screen = Screen.SEARCH }) { Icon(Icons.Default.Search, "Search", tint = Color.White) }
            }
            IconButton(onClick = { actions.dial("") }) { Icon(Icons.Default.Call, "Phone", tint = Color(0xFF5CFF8A)) }
            IconButton(onClick = actions.settings) { Icon(Icons.Default.Settings, "Flip settings", tint = Color.White) }
        }
    }
}

@Composable
private fun FavoritesGrid(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 3) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 3) {
                    val slot = row * 3 + col
                    val pkg = ui.settings.favorites.getOrNull(slot)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onLongClick = { nav.picking = slot }) { if (pkg != null) actions.open(pkg) else nav.picking = slot },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (pkg == null) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 24.sp, color = Color(0x88FFFFFF))
                            }
                            return@Column
                        }
                        val v = ui.verdict(pkg)
                        Box {
                            AppIcon(pkg, 52.dp)
                            if (v.blocked) {
                                Surface(shape = CircleShape, color = Color(0xFFFFC94A), modifier = Modifier.size(20.dp).align(Alignment.BottomEnd)) {
                                    Icon(Icons.Default.Lock, "Blocked", tint = Color.Black, modifier = Modifier.padding(3.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val label = ui.apps.find { it.pkg == pkg }?.label ?: pkg.substringAfterLast('.')
                        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = shortStatus(v)
                        if (status.isNotEmpty()) Text(status, color = Color(0xFFFFE08A), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernResults(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(t9Results(nav.digits, ui.apps, ui.contacts)) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { if (m.pkg != null) actions.open(m.pkg) else m.contact?.let { actions.dial(it.number) } }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.pkg != null) AppIcon(m.pkg, 36.dp) else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = Color.White)
                        val sub = m.pkg?.let { describe(ui.verdict(it)) } ?: m.contact?.number.orEmpty()
                        if (sub.isNotEmpty()) Text(sub, color = Color(0xFF9CC9FF), fontSize = 12.sp)
                    }
                    if (m.pkg != null && ui.verdict(m.pkg).blocked) Icon(Icons.Default.Lock, "Blocked", tint = Color(0xFFFFC94A))
                }
            }
        }
        Text(nav.digits, color = Color.White, fontSize = 28.sp, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun ModernKeypad(nav: HomeNav, actions: HomeActions) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    val sub = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in row) {
                    Column(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x22FFFFFF))
                            .combinedClickable(onLongClick = if (k == "0") ({ nav.type('+') }) else null) { nav.type(k[0]) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k, color = Color.White, fontSize = 20.sp)
                        sub[k]?.let { Text(it, color = Color(0xFF9CC9FF), fontSize = 9.sp) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { actions.dial(nav.digits) }, Modifier.weight(2f)) {
                Icon(Icons.Default.Call, null)
                Spacer(Modifier.width(8.dp))
                Text("Call")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clip(CircleShape).combinedClickable(onLongClick = { nav.digits = "" }) { nav.erase() }.padding(12.dp),
                ) { Icon(Icons.Default.Close, "Delete", tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}
