package com.example.sightblocker

import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.sightblocker.admin.DeviceOwnerEnforcer
import com.example.sightblocker.admin.PolicyController
import com.example.sightblocker.engine.BlockingEngine
import com.example.sightblocker.engine.Outcome
import com.example.sightblocker.engine.PrefsBlockStore
import com.example.sightblocker.engine.Status
import com.example.sightblocker.engine.UnlockPhrase
import java.util.Locale

/** One page: status, block, unblock. All rules live in [BlockingEngine]; this only shows state. */
class MainActivity : AppCompatActivity() {

    private lateinit var policy: PolicyController
    private lateinit var engine: BlockingEngine

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var btnBlock: Button
    private lateinit var unblockHelp: TextView
    private lateinit var phraseInput: EditText
    private lateinit var btnUnblock: Button
    private lateinit var btnCancel: Button
    private lateinit var waitLabel: String
    private lateinit var ownerCard: View
    private lateinit var btnReleaseOwner: Button

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Debug builds use short timers so the whole flow can be tested in a minute.
        // Release builds always use the real ones; there is no switch to shorten them.
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        waitLabel = if (isDebuggable) "30 second" else "20 minute"
        policy = PolicyController(this)
        engine = if (isDebuggable) {
            BlockingEngine(
                PrefsBlockStore(this), DeviceOwnerEnforcer(policy),
                waitMs = BlockingEngine.DEBUG_WAIT_MS, readyWindowMs = BlockingEngine.DEBUG_READY_WINDOW_MS,
            )
        } else {
            BlockingEngine(PrefsBlockStore(this), DeviceOwnerEnforcer(policy))
        }

        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        btnBlock = findViewById(R.id.btnBlock)
        unblockHelp = findViewById(R.id.unblockHelp)
        phraseInput = findViewById(R.id.phraseInput)
        btnUnblock = findViewById(R.id.btnUnblock)
        btnCancel = findViewById(R.id.btnCancel)
        findViewById<TextView>(R.id.phraseText).text = UnlockPhrase.TEXT

        setUpPhraseInput()

        btnBlock.setOnClickListener { handle(engine.block()) }
        btnCancel.setOnClickListener { handle(engine.cancelUnlock()) }
        btnUnblock.setOnClickListener {
            val typed = phraseInput.text.toString()
            val outcome = when (engine.state().status) {
                Status.BLOCKED -> engine.requestUnlock(typed)
                Status.READY -> engine.confirmUnlock(typed)
                else -> Outcome.Rejected("Not available right now.")
            }
            handle(outcome)
        }

        ownerCard = findViewById(R.id.ownerCard)
        btnReleaseOwner = findViewById(R.id.btnReleaseOwner)
        btnReleaseOwner.setOnClickListener { confirmReleaseOwner() }
    }

    override fun onResume() {
        super.onResume()
        engine.reconcile()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    /** No paste, no long-press menu, no suggestions: the phrase must be typed by hand. */
    private fun setUpPhraseInput() {
        phraseInput.isLongClickable = false
        val noMenu = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
        phraseInput.customSelectionActionModeCallback = noMenu
        phraseInput.customInsertionActionModeCallback = noMenu

        phraseInput.addTextChangedListener(object : TextWatcher {
            private var before = ""
            private var restoring = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                before = s?.toString().orEmpty()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // More than one character appearing at once means paste or autocomplete. Undo it.
                if (!restoring && count - before > 1) {
                    restoring = true
                    phraseInput.setText(this.before)
                    phraseInput.setSelection(this.before.length)
                    restoring = false
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (!restoring) render()
            }
        })
    }

    /** Only while unblocked: ask once, then give up Device Owner. */
    private fun confirmReleaseOwner() {
        if (engine.state().status != Status.UNBLOCKED || !policy.isDeviceOwner()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.release_dialog_title)
            .setMessage(R.string.release_dialog_message)
            .setPositiveButton(R.string.release_dialog_yes) { _, _ ->
                if (engine.state().status == Status.UNBLOCKED && policy.isDeviceOwner()) {
                    policy.releaseDeviceOwner()
                }
                render()
            }
            .setNegativeButton(R.string.release_dialog_no, null)
            .show()
    }

    private fun handle(outcome: Outcome) {
        if (outcome is Outcome.Rejected) {
            Toast.makeText(this, outcome.reason, Toast.LENGTH_SHORT).show()
        }
        phraseInput.setText("")
        render()
    }

    private fun render() {
        val state = engine.state()
        val owner = policy.isDeviceOwner()
        val status = state.status
        val locked = status != Status.UNBLOCKED

        // 1. Status
        statusTitle.setText(if (locked) R.string.state_blocked else R.string.state_unblocked)
        statusTitle.setTextColor(getColor(if (locked) R.color.status_blocked else R.color.status_unblocked))
        statusDetail.text = when {
            !owner -> getString(R.string.detail_not_owner)
            status == Status.WAITING -> getString(R.string.detail_waiting, formatRemaining(state.remainingMs))
            status == Status.READY -> getString(R.string.detail_ready, formatRemaining(state.remainingMs))
            status == Status.BLOCKED -> getString(R.string.detail_blocked)
            else -> getString(R.string.detail_unblocked)
        }

        // 2. Block: only usable while unblocked
        btnBlock.isEnabled = owner && status == Status.UNBLOCKED

        // 3. Unblock
        unblockHelp.text = when (status) {
            Status.UNBLOCKED -> getString(R.string.help_unblocked)
            Status.BLOCKED -> getString(R.string.help_blocked, waitLabel)
            Status.WAITING -> getString(R.string.help_waiting)
            Status.READY -> getString(R.string.help_ready)
        }
        val canType = status == Status.BLOCKED || status == Status.READY
        phraseInput.isEnabled = canType
        btnUnblock.setText(
            when (status) {
                Status.UNBLOCKED -> R.string.btn_unblock_idle
                Status.BLOCKED -> R.string.btn_request_unblock
                Status.WAITING -> R.string.btn_waiting
                Status.READY -> R.string.btn_confirm_unblock
            }
        )
        btnUnblock.isEnabled = canType && UnlockPhrase.matches(phraseInput.text.toString())
        btnCancel.visibility = if (status == Status.WAITING || status == Status.READY) View.VISIBLE else View.GONE

        // 4. Device Owner: only while unblocked, and only if we are owner
        ownerCard.visibility = if (!locked && owner) View.VISIBLE else View.GONE
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
