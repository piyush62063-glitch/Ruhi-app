package com.sumit.ruhi

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val Bg = Color(0xFF060512)
val Pur = Color(0xFF8B5CF6)
val Pink = Color(0xFFEC4899)
val Navy = Color(0xFF14103A)
val Orange = Color(0xFFFFA83D)
val Line = Color(0x558B5CF6)

@Composable
fun RuhiRoot(vm: Vm) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Pur)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            if (vm.screen == "listen") {
                ListenScreen(vm)
            } else {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        when (vm.screen) {
                            "chat" -> ChatScreen(vm)
                            "history" -> HistoryScreen(vm)
                            "settings" -> SettingsScreen(vm)
                            else -> HomeScreen(vm)
                        }
                    }
                    BottomBar(vm)
                }
            }
        }
    }
}

@Composable
fun Plasma(d: Dp, base: Color, light: Color, glow: Color, fast: Boolean) {
    val t = rememberInfiniteTransition(label = "plasma")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(if (fast) 5000 else 11000, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(Modifier.size(d)) {
        val full = size.minDimension / 2
        val r = full * 0.78f
        val c = center
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = c, radius = full), radius = full, center = c)
        drawCircle(Brush.radialGradient(listOf(base, base, light.copy(alpha = 0.65f)), center = c, radius = r), radius = r, center = c)
        for (i in 0 until 5) {
            val ph = a + i * 1.3f
            val o = Offset(c.x + cos(ph * (1f + i * 0.15f)) * r * 0.35f, c.y + sin(ph * (0.8f + i * 0.2f)) * r * 0.35f)
            val br = r * (0.38f + 0.06f * sin(ph * 2f))
            drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.75f), Color.Transparent), center = o, radius = br), radius = br, center = o)
        }
        val h = Offset(c.x - r * 0.35f, c.y - r * 0.35f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center = h, radius = r * 0.28f), radius = r * 0.28f, center = h)
    }
}

@Composable
fun Stars() {
    val pts = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        pts.forEach { (x, y, z) ->
            drawCircle(Color.White.copy(alpha = 0.15f + 0.5f * z), radius = 0.8f + 2f * z, center = Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
fun Logo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text("RUHI", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.width(8.dp))
            Text("AI", color = Pur, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }
        Text("YOUR AI ASSISTANT", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 4.sp)
    }
}

@Composable
fun SqBtn(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Navy)
            .border(1.dp, Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(t, color = Color.White, fontSize = 20.sp) }
}

@Composable
fun Nav(icon: String, label: String, sel: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 22.sp, modifier = Modifier.alpha(if (sel) 1f else 0.5f))
        Text(label, fontSize = 12.sp, color = if (sel) Orange else Color.White.copy(alpha = 0.5f))
        Box(Modifier.padding(top = 2.dp).width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) Orange else Color.Transparent))
    }
}

@Composable
fun BottomBar(vm: Vm) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 18.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xFF1B1550))
                .border(1.dp, Line, RoundedCornerShape(30.dp)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            Nav("🏠", "Home", vm.screen == "home") { vm.screen = "home" }
            Nav("💬", "Chat", vm.screen == "chat") { vm.screen = "chat" }
            Spacer(Modifier.width(64.dp))
            Nav("🕘", "History", vm.screen == "history") { vm.screen = "history" }
            Nav("⚙️", "Settings", vm.screen == "settings") { vm.screen = "settings" }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-28).dp).size(66.dp).clip(CircleShape)
                .border(3.dp, Color(0xFFD9A441), CircleShape).background(Navy).clickable { vm.startVoice() },
            contentAlignment = Alignment.Center
        ) {
            val av = vm.avatar
            if (av != null) Image(bitmap = av, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            else Text("R", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AskBar(text: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Navy)
            .border(1.dp, Line, RoundedCornerShape(30.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF2A1F5C)), contentAlignment = Alignment.Center) { Text("✨", fontSize = 18.sp) }
        TextField(
            value = text, onValueChange = onChange, modifier = Modifier.weight(1f),
            placeholder = { Text("Ask me anything...") }, singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Box(
            Modifier.width(56.dp).height(46.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF6D3AF0)))).clickable { onSend() },
            contentAlignment = Alignment.Center
        ) { Text("➤", color = Color.White, fontSize = 20.sp) }
    }
}

@Composable
fun FeatureCard(icon: String, label: String, m: Modifier, onClick: () -> Unit) {
    Column(
        m.clip(RoundedCornerShape(20.dp)).background(Navy).border(1.dp, Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2A1F5C)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
fun Dots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(33) { i ->
            Box(Modifier.width(3.dp).height(if (i % 11 == 5) 16.dp else 3.dp).clip(CircleShape).background(Pur.copy(alpha = 0.8f)))
        }
    }
}

@Composable
fun HomeScreen(vm: Vm) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SqBtn("☰") { vm.screen = "history" }
            Logo()
            SqBtn("⚙") { vm.screen = "settings" }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            val av = vm.avatar
            if (av != null) {
                Image(
                    bitmap = av, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
                )
                Box(Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
            } else {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Namaste, ${vm.name} 👋", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Photo lagane ke liye Settings > Ruhi ki photo", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                Box(Modifier.align(Alignment.Center).offset(y = (-24).dp)) {
                    Plasma(230.dp, Color(0xFF241068), Pur, Color(0xFFA78BFA), false)
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(118.dp)
                    .border(5.dp, Brush.sweepGradient(listOf(Pur, Color(0xFF60A5FA), Pink, Pur)), CircleShape)
                    .padding(8.dp).clip(CircleShape).background(Color(0xFF110C2E)).clickable { vm.startVoice() },
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 42.sp) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tap to Talk", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Dots()
        }
        Spacer(Modifier.height(12.dp))
        AskBar(q, { q = it }) {
            if (q.isNotBlank()) { vm.screen = "chat"; vm.send(q.trim()); q = "" }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard("💬", "Chat", Modifier.weight(1f)) { vm.screen = "chat" }
            FeatureCard("🎨", "Create Image", Modifier.weight(1f)) { vm.draft = "Ek image ka detailed prompt banao: "; vm.screen = "chat" }
            FeatureCard("📝", "Write", Modifier.weight(1f)) { vm.draft = "Mere liye likho: "; vm.screen = "chat" }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ChatScreen(vm: Vm) {
    var text by remember { mutableStateOf("") }
    val ls = rememberLazyListState()
    LaunchedEffect(vm.draft) { if (vm.draft.isNotEmpty()) { text = vm.draft; vm.draft = "" } }
    LaunchedEffect(vm.msgs.size, vm.busy) {
        val n = vm.msgs.size + (if (vm.busy) 1 else 0)
        if (n > 0) ls.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SqBtn("←") { vm.screen = "home" }
            Spacer(Modifier.width(12.dp))
            Text("Chat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = ls, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.msgs.isEmpty()) item { Text("Kuch bhi poocho ya bolo: \"youtube pe arijit ka gana\" 🎵", color = Color.White.copy(alpha = 0.5f)) }
            itemsIndexed(vm.msgs) { _, m ->
                val me = m.first
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (me) Pur else Navy)
                            .border(1.dp, if (me) Color.Transparent else Line, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text(m.second, color = Color.White, fontSize = 15.sp) }
                }
            }
            if (vm.busy) item { Text("Ruhi soch rahi hai...", color = Color.White.copy(alpha = 0.5f)) }
        }
        AskBar(text, { text = it }) {
            if (text.isNotBlank()) { vm.send(text.trim()); text = "" }
        }
    }
}

@Composable
fun HistoryScreen(vm: Vm) {
    val asked = vm.msgs.filter { it.first }.map { it.second }.reversed()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SqBtn("←") { vm.screen = "home" }
            Spacer(Modifier.width(12.dp))
            Text("History", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        if (asked.isEmpty()) Text("Abhi koi chat nahi hai.", color = Color.White.copy(alpha = 0.5f))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(asked) { _, s ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Navy)
                        .border(1.dp, Line, RoundedCornerShape(18.dp)).clickable { vm.screen = "chat" }.padding(14.dp)
                ) { Text(s, color = Color.White, fontSize = 15.sp, maxLines = 2) }
            }
        }
    }
}

@Composable
fun RoundBtn(icon: String, label: String, size: Dp, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg).border(1.dp, Line, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 26.sp, color = Color.White) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
fun ListenScreen(vm: Vm) {
    val (base, light, glow) = when (vm.phase) {
        "listen" -> Triple(Color(0xFF0B3C4D), Color(0xFF2EE6D0), Color(0xFFD6FFFA))
        "connect", "think" -> Triple(Color(0xFF3B0F5C), Color(0xFFEC4899), Color(0xFFF59E5B))
        else -> Triple(Color(0xFF241068), Color(0xFF8B5CF6), Color(0xFFA78BFA))
    }
    val title = when (vm.phase) {
        "connect" -> "Connecting to ruhi..."
        "listen" -> "ruhi is listening..."
        "think" -> "ruhi is thinking..."
        "speak" -> "ruhi is speaking..."
        else -> "Tap the orb to talk"
    }
    val sub = when {
        vm.muted -> "Mic mute hai. Unmute dabao."
        vm.phase == "listen" && vm.partial.isNotBlank() -> vm.partial
        vm.note.isNotBlank() -> vm.note
        else -> "Orb dabao aur bolo."
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1245), Color(0xFF07051A))))) {
        Stars()
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ruhi", color = Color.White, fontSize = 40.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Navy).border(1.dp, Line, CircleShape).clickable { vm.screen = "home" },
                    contentAlignment = Alignment.Center
                ) { Text("⌄", color = Color.White, fontSize = 24.sp) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.listen() }) {
                Plasma(300.dp, base, light, glow, vm.phase == "listen" || vm.phase == "think" || vm.phase == "connect")
            }
            Spacer(Modifier.height(24.dp))
            Text(title, color = Color.White, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp, textAlign = TextAlign.Center)
            if (vm.lastReply.isNotBlank() && vm.phase != "listen") {
                Spacer(Modifier.height(10.dp))
                Text(
                    vm.lastReply, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState())
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                RoundBtn(if (vm.muted) "🔇" else "🎤", "Mute", 66.dp, Navy) { vm.toggleMute() }
                RoundBtn("📞", "End", 84.dp, Color(0xFFF07178)) { vm.stop(); vm.screen = "home" }
                RoundBtn("⌄", "Minimize", 66.dp, Navy) { vm.screen = "home" }
            }
        }
    }
}

@Composable
fun SHead(icon: String, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = Pur, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 14.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
fun SCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Navy)
            .border(1.dp, Line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
fun SRow(icon: String, title: String, sub: String, chip: String? = null, chipGood: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF5B21B6)))),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp)
            Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        if (chip != null) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (chipGood) Color(0xFF123B45) else Color(0xFF4A3520)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(chip, color = if (chipGood) Color(0xFF2DD4BF) else Orange)
            }
        }
        Text("›", color = Color.White.copy(alpha = 0.7f), fontSize = 26.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun Divider1() { Box(Modifier.fillMaxWidth().height(1.dp).background(Line)) }

@Composable
fun SettingsScreen(vm: Vm) {
    var open by remember { mutableStateOf("") }
    var n by remember { mutableStateOf(vm.name) }
    var k by remember { mutableStateOf(vm.key) }
    var v by remember { mutableStateOf(vm.voiceOn) }
    var saved by remember { mutableStateOf(false) }
    fun tog(s: String) { open = if (open == s) "" else s }
    val fc = TextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF1E1850), unfocusedContainerColor = Color(0xFF1E1850),
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SqBtn("←") { vm.screen = "home" }
            Spacer(Modifier.weight(1f))
            Logo()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(46.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Settings", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
        Text("Customize your AI experience", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        SCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF1E1850)).border(2.dp, Pur, CircleShape), contentAlignment = Alignment.Center) {
                    Text("R", color = Pur, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Ruhi AI", color = Color.White, fontSize = 22.sp)
                    Text("Your Personal AI Assistant", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                    Text("v3.0", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                    Text("Made in India 🇮🇳", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        SHead("✨", "AI SETTINGS", "Configure your AI assistant")
        SCard {
            SRow("🔑", "Connections", "Gemini key aur naam", if (vm.key.isNotBlank()) "Ready" else "Add key", vm.key.isNotBlank()) { tog("conn") }
            if (open == "conn") {
                Column(Modifier.padding(bottom = 14.dp)) {
                    Text("Aapka naam", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    TextField(n, { n = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, colors = fc, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Gemini API Key", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    TextField(
                        k, { k = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, colors = fc, shape = RoundedCornerShape(14.dp),
                        visualTransformation = PasswordVisualTransformation(), placeholder = { Text("AIza...") }
                    )
                    Text("Key sirf aapke phone mein save hoti hai.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.save(n, k, v); saved = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pur)
                    ) { Text(if (saved) "Saved ✓" else "Save", fontSize = 16.sp) }
                }
            }
            Divider1()
            SRow("🗣", "Voice & language", "Hinglish · hi-IN · reply ${if (v) "on" else "off"}") { tog("voice") }
            if (open == "voice") {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ruhi jawab bolke sunaye", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = v, onCheckedChange = { v = it; vm.save(n, k, it) })
                }
            }
            Divider1()
            SRow("💬", "Chats & memory", "${vm.msgs.size} saved messages") { tog("chats") }
            if (open == "chats") {
                Button(
                    onClick = { vm.clearChats() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A1D3A))
                ) { Text("Saari chats delete karo") }
            }
        }
        SHead("⚙️", "APP SETTINGS", "Appearance and behavior")
        SCard {
            SRow("📷", "Ruhi ki photo", "Home screen pe apni pasand ki photo lagao", "Choose", false) { vm.pickPhoto() }
            Divider1()
            SRow("🎨", "Look & feel", "Dark · Plasma orb") { }
            Divider1()
            SRow("🛡", "Phone control", "Quick actions aur commands") { tog("phone") }
            if (open == "phone") {
                Text(
                    "Bol ke dekho:\n• youtube pe <gana> chalao\n• call 98xxxxxxxx\n• whatsapp 98xxxxxxxx message <text>\n• navigate <jagah>\n• open <app ka naam>",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
        SHead("ℹ️", "ABOUT", "App information and support")
        SCard {
            SRow("🔒", "Privacy & about", "Aapka data, version") { tog("about") }
            if (open == "about") {
                Text(
                    "Key aur chats sirf aapke phone mein save hote hain. Sawal Google Gemini ko bheje jaate hain jawab ke liye.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

