package com.sumit.ruhi

import android.content.Context

object Core {
    /** Runs on a background thread. sp == null means typed chat (no speaking). */
    fun process(ctx: Context, text: String, sp: Speaker?, g: Int) {
        Hub.addMsg(true, text)
        if (sp != null) Hub.setState(Hub.THINKING)
        var first = true
        try {
            val reply = Brain.ask(text,
                { s ->
                    if (sp != null) {
                        if (first) {
                            first = false
                            Hub.setState(Hub.SPEAKING)
                        }
                        sp.say(g, s)
                    }
                },
                { tag ->
                    val err = Actions.run(ctx, tag)
                    if (err != null) {
                        Hub.addMsg(false, err)
                        sp?.say(g, err)
                    }
                })
            if (reply.isNotBlank()) Hub.addMsg(false, reply)
        } catch (e: Exception) {
            val m = if (e.message == "nokey") "Pehle Settings me Groq ya Gemini key daal do."
            else "Network thoda slow hai, ek baar phir bolna."
            Hub.addMsg(false, m)
            sp?.say(g, m)
        }
    }
}
