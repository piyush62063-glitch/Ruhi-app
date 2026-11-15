package com.sumit.ruhi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private val BG = 0xFF0B0820.toInt()
    private val CARD = 0xFF161233.toInt()
    private val ACC = 0xFF9B6BFF.toInt()
    private val TXT = 0xFFEDE9FE.toInt()
    private val SUB = 0xFF9A93C0.toInt()
    private val GOLD = 0xFFE8B96A.toInt()

    private lateinit var pages: List<View>
    private lateinit var navItems: List<TextView>
    private lateinit var orb: OrbView
    private lateinit var titleTv: TextView
    private lateinit var sub: TextView
    private lateinit var reply: TextView
    private lateinit var btnMute: Button
    private lateinit var btnEnd: Button
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var avatarBox: FrameLayout
    private var avatarImg: ImageView? = null
    private var tab = 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, r: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        pages = listOf(buildHome(), buildChat(), buildStyle(), buildSettings())
        val content = FrameLayout(this)
        for (p in pages) content.addView(p, FrameLayout.LayoutParams(-1, -1))
        root.addView(content, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(84) })
        root.addView(buildNav(), FrameLayout.LayoutParams(-1, dp(104), Gravity.BOTTOM))
        setContentView(root)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        Hub.onChange = { refresh() }
        orb.style = Store.orbStyle
        loadAvatar()
        refresh()
    }

    override fun onPause() {
        Hub.onChange = null
        super.onPause()
    }

    // ---------- Home ----------
    private fun buildHome(): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(24), dp(16), dp(24), dp(8))
        c.addView(TextView(this).apply {
            text = "ruhi"; setTextColor(TXT); textSize = 34f; typeface = Typeface.SERIF
        })
        orb = OrbView(this).apply {
            live = true
            style = Store.orbStyle
            setOnClickListener { onOrb() }
        }
        c.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))
        titleTv = TextView(this).apply {
            textSize = 26f; setTextColor(TXT); gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        sub = TextView(this).apply { textSize = 14f; setTextColor(SUB); gravity = Gravity.CENTER; minLines = 2 }
        reply = TextView(this).apply { textSize = 16f; setTextColor(TXT); gravity = Gravity.CENTER; maxLines = 4 }
        c.addView(titleTv, LinearLayout.LayoutParams(-1, -2))
        c.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        c.addView(reply, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        btnMute = btn("Mute", 0xFF2A2350.toInt()) { if (Hub.running) RuhiService.cmd(this, RuhiService.A_MUTE) else toast("Pehle Start dabao") }
        btnEnd = btn("Start", ACC) { if (Hub.running) RuhiService.cmd(this, RuhiService.A_STOP) else startTalk() }
        val btnMin = btn("Minimize", 0xFF2A2350.toInt()) { moveTaskToBack(true) }
        for (x in listOf(btnMute, btnEnd, btnMin)) {
            row.addView(x, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return c
    }

    private fun onOrb() {
        if (!Hub.running) startTalk() else RuhiService.cmd(this, RuhiService.A_SKIP)
    }

    private fun refresh() {
        orb.style = Store.orbStyle
        titleTv.text = when {
            !Hub.running -> "Tap the orb to talk"
            Hub.muted -> "Muted"
            else -> when (Hub.state) {
                Hub.LISTENING -> "Listening…"
                Hub.THINKING -> "Thinking…"
                Hub.SPEAKING -> "Ruhi bol rahi hai"
                else -> "Tap the orb to talk"
            }
        }
        sub.text = if (Hub.status.isNotEmpty()) Hub.status else Hub.partial
        val last = synchronized(Hub.msgs) { Hub.msgs.lastOrNull { !it.first }?.second ?: "" }
        reply.text = last
        btnMute.text = if (Hub.muted) "Unmute" else "Mute"
        btnEnd.text = if (Hub.running) "End" else "Start"
        if (tab == 1) rebuildChat()
    }

    private fun startTalk() {
        val all = ArrayList<String>()
        all.add(Manifest.permission.RECORD_AUDIO)
        all.add(Manifest.permission.READ_CONTACTS)
        all.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) all.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = all.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val noMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (noMic || (need.isNotEmpty() && !Store.permAsked)) {
            Store.permAsked = true
            requestPermissions(need.toTypedArray(), 7)
            return
        }
        RuhiService.cmd(this, RuhiService.A_START)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            RuhiService.cmd(this, RuhiService.A_START)
        } else {
            toast("Mic permission ke bina Ruhi sun nahi sakti")
        }
    }

    // ---------- Chat ----------
    private fun buildChat(): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(16), dp(16), dp(8))
        c.addView(TextView(this).apply { text = "Chat"; setTextColor(TXT); textSize = 26f; typeface = Typeface.SERIF })
        chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatScroll = ScrollView(this)
        chatScroll.addView(chatList)
        c.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        chatInput = edit("Ruhi se kuch bhi pucho…", false)
        chatInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        row.addView(chatInput, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn("Send", ACC) { sendTyped() }, LinearLayout.LayoutParams(dp(84), dp(48)).apply { leftMargin = dp(8) })
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return c
    }

    private fun sendTyped() {
        val t = chatInput.text.toString().trim()
        if (t.isEmpty()) return
        chatInput.setText("")
        val ctx = applicationContext
        Thread { Core.process(ctx, t, null, 0) }.start()
    }

    private fun rebuildChat() {
        chatList.removeAllViews()
        val copy = synchronized(Hub.msgs) { ArrayList(Hub.msgs) }
        for (m in copy) {
            val tv = TextView(this).apply {
                text = m.second
                textSize = 15f
                setTextColor(TXT)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(if (m.first) 0xFF5B3FC4.toInt() else CARD, 16)
                maxWidth = dp(280)
            }
            chatList.addView(tv, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = if (m.first) Gravity.END else Gravity.START
                topMargin = dp(6)
            })
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Style (orb + photo) ----------
    private fun buildStyle(): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(24))
        sv.addView(col)
        col.addView(TextView(this).apply { text = "Style"; setTextColor(TXT); textSize = 26f; typeface = Typeface.SERIF })

        val names = listOf("Plasma", "Aurora", "Pulse", "Galaxy")
        val boxes = ArrayList<LinearLayout>()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun hl() {
            for (i in boxes.indices) {
                boxes[i].background = GradientDrawable().apply {
                    setColor(0xFF0F0B26.toInt())
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(2), if (i == Store.orbStyle) ACC else 0x00000000)
                }
            }
        }
        for (i in names.indices) {
            val ov = OrbView(this)
            ov.style = i
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(4), dp(4), dp(4), dp(6))
            box.setOnClickListener {
                Store.orbStyle = i
                orb.style = i
                hl()
            }
            box.addView(ov, LinearLayout.LayoutParams(-1, dp(84)))
            box.addView(TextView(this).apply { text = names[i]; textSize = 12f; setTextColor(TXT); gravity = Gravity.CENTER })
            boxes.add(box)
            row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        hl()
        col.addView(card(label("Orb style chuno (tap karke select karo)"), row), cardLp())

        val photoBtn = btn("Ruhi ki photo chuno", ACC) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 21)
        }
        col.addView(card(label("Neeche bar ke beech wali gol photo"), photoBtn), cardLp())
        return sv
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val uri: Uri? = data?.data
        if (rc == 21 && res == RESULT_OK && uri != null) {
            try {
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
                val s = minOf(bmp.width, bmp.height)
                val sq = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
                val out = Bitmap.createScaledBitmap(sq, 300, 300, true)
                File(filesDir, "avatar.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                loadAvatar()
                toast("Photo set ho gayi")
            } catch (e: Exception) {
                toast("Photo nahi lag paayi")
            }
        }
    }

    private fun loadAvatar() {
        val f = File(filesDir, "avatar.png")
        val iv = avatarImg ?: return
        if (f.exists()) {
            iv.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
            iv.visibility = View.VISIBLE
        } else {
            iv.visibility = View.GONE
        }
    }

    // ---------- Settings ----------
    private fun buildSettings(): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(24))
        sv.addView(col)
        col.addView(TextView(this).apply { text = "Settings"; setTextColor(TXT); textSize = 26f; typeface = Typeface.SERIF })
        col.addView(label("Keys sirf is phone me save hoti hain."), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        col.addView(keyCard(
            "Chat - Groq keys",
            "Fast replies. Kai keys comma (,) se alag karke daal sakte ho, rate limit pe baari-baari use hongi.",
            "Paste Groq key(s)", { Store.groqKeys.isNotBlank() }, "https://console.groq.com/keys"
        ) { Store.groqKeys = it }, cardLp())

        col.addView(keyCard(
            "Gemini key",
            "Backup brain aur Gemini voice (Kore) ke liye.",
            "Paste Gemini key", { Store.geminiKey.isNotBlank() }, "https://aistudio.google.com/apikey"
        ) { Store.geminiKey = it }, cardLp())

        col.addView(keyCard(
            "ElevenLabs key (optional)",
            "ElevenLabs wali premium awaaz ke liye.",
            "Paste ElevenLabs key", { Store.elevenKey.isNotBlank() }, "https://elevenlabs.io/app/settings/api-keys"
        ) { Store.elevenKey = it }, cardLp())

        // voice + language
        val engines = listOf("Phone voice (sabse fast)", "Gemini voice (Kore)", "ElevenLabs voice")
        val eng = radio(engines, Store.voiceEngine) { Store.voiceEngine = it }
        val gv = edit("Gemini voice naam (Kore, Aoede, Puck…)", false)
        gv.setText(Store.geminiVoice)
        val ev = edit("ElevenLabs voice ID", false)
        ev.setText(Store.elevenVoiceId)
        val langs = listOf("Hindi (hi-IN)", "Hinglish (en-IN)")
        val lang = radio(langs, if (Store.lang == "en-IN") 1 else 0) { Store.lang = if (it == 1) "en-IN" else "hi-IN" }
        val nm = edit("Tumhara naam", false)
        nm.setText(Store.userName)
        col.addView(card(
            label("Voice aur language"), eng, gv, ev, label("Tum kis language me bologe?"), lang, label("Ruhi tumhe kya bulaye?"), nm,
            btn("Save", ACC) {
                Store.geminiVoice = gv.text.toString().trim().ifEmpty { "Kore" }
                Store.elevenVoiceId = ev.text.toString().trim().ifEmpty { "EXAVITQu4vr4xnSDxMzb" }
                Store.userName = nm.text.toString().trim().ifEmpty { "Sumit" }
                toast("Saved")
            }
        ), cardLp())

        // permissions
        val ovl = label(overlayText())
        col.addView(card(
            label("Background me apps kholne ke liye"),
            label("'Display over other apps' ON karo, tabhi app ke bahar rehkar bhi YouTube/WhatsApp wagairah khulenge."),
            ovl,
            btn("Overlay permission kholo", ACC) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        ), cardLp())
        col.addView(label("Ruhi v6"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return sv
    }

    private fun overlayText(): String =
        if (Settings.canDrawOverlays(this)) "Status: ON" else "Status: OFF"

    // ---------- helpers ----------
    private fun label(t: String): TextView = TextView(this).apply { text = t; textSize = 14f; setTextColor(SUB) }

    private fun edit(hint: String, pwd: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF6E678F.toInt())
        setTextColor(TXT)
        textSize = 15f
        setSingleLine(true)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(0xFF0F0B26.toInt(), 14, 0xFF2A2350.toInt())
        if (pwd) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun btn(t: String, fill: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 15f
        background = rounded(fill, 26)
        setOnClickListener { onClick() }
    }

    private fun cardLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

    private fun card(vararg v: View): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        c.background = rounded(CARD, 20, 0xFF2D2760.toInt())
        for (x in v) c.addView(x, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return c
    }

    private fun radio(options: List<String>, current: Int, onSel: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        for (i in options.indices) {
            g.addView(RadioButton(this).apply {
                text = options[i]
                setTextColor(TXT)
                id = 100 + i
                isChecked = i == current
            })
        }
        g.setOnCheckedChangeListener { _, id -> onSel(id - 100) }
        return g
    }

    private fun keyCard(t: String, d: String, hint: String, has: () -> Boolean, url: String, save: (String) -> Unit): View {
        val st = TextView(this)
        st.textSize = 13f
        fun upd() {
            st.text = if (has()) "✓ Ready" else "⚠ Add a key"
            st.setTextColor(if (has()) 0xFF2DD4BF.toInt() else 0xFFFFB020.toInt())
        }
        upd()
        val e = edit(hint, true)
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(TextView(this).apply { text = t; textSize = 17f; setTextColor(TXT) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(st)
        val link = TextView(this).apply {
            text = "Get a free key ↗"
            textSize = 14f
            setTextColor(ACC)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        return card(head, label(d), e, btn("Save key", ACC) {
            val v = e.text.toString().trim()
            if (v.isNotEmpty()) {
                save(v)
                e.setText("")
                upd()
                toast("Saved")
            } else {
                toast("Pehle key paste karo")
            }
        }, link)
    }

    // ---------- bottom nav ----------
    private fun buildNav(): View {
        val fl = FrameLayout(this)
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = rounded(CARD, 32, 0xFF2D2760.toInt())
        fun item(t: String, i: Int): TextView = TextView(this).apply {
            text = t
            gravity = Gravity.CENTER
            textSize = 14f
            setOnClickListener { showTab(i) }
        }
        navItems = listOf(item("Home", 0), item("Chat", 1), item("Style", 2), item("Settings", 3))
        bar.addView(navItems[0], LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(navItems[1], LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(Space(this), LinearLayout.LayoutParams(dp(84), 1))
        bar.addView(navItems[2], LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(navItems[3], LinearLayout.LayoutParams(0, -1, 1f))
        fl.addView(bar, FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(14)) })

        avatarBox = FrameLayout(this)
        avatarBox.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF2A1670.toInt())
            setStroke(dp(3), GOLD)
        }
        avatarBox.addView(TextView(this).apply {
            text = "R"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }, FrameLayout.LayoutParams(-1, -1))
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
        }
        iv.clipToOutline = true
        avatarImg = iv
        avatarBox.addView(iv, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        avatarBox.setOnClickListener { if (Hub.running) RuhiService.cmd(this, RuhiService.A_STOP) else startTalk() }
        fl.addView(avatarBox, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(26) })
        return fl
    }

    private fun showTab(i: Int) {
        tab = i
        for (j in pages.indices) pages[j].visibility = if (j == i) View.VISIBLE else View.GONE
        for (j in navItems.indices) navItems[j].setTextColor(if (j == i) GOLD else SUB)
        refresh()
    }
}
