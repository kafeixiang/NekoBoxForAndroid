package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import io.nekohasekai.sfa.utils.ColorUtils
import libbox.Libbox
import moe.matsuri.nb4a.utils.SendLog

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutLogcatBinding

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        if (Build.VERSION.SDK_INT >= 23) {
            binding.textview.breakStrategy = 0 // simple
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        reloadSession()
        // TODO new logcat
    }

    private fun reloadSession() {
        binding.textview.text = ColorUtils.ansiEscapeToSpannable(
            requireContext(), String(SendLog.getNekoLog(50 * 1024))
        )
        binding.scrollview.clearFocus()
        binding.scrollview.post {
            binding.scrollview.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                runOnDefaultDispatcher {
                    try {
                        Libbox.nekoLogClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                        onMainDispatcher { binding.textview.text = "" }
                    } catch (e: Exception) {
                        onMainDispatcher { snackbar(e.readableMessage).show() }
                    }
                }
            }
            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "NB4A")
                }
            }
            R.id.action_refresh -> {
                reloadSession()
            }
        }
        return true
    }
}