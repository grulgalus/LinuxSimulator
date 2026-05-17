package com.linuxsim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.linuxsim.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vfs: VirtualFileSystem
    private lateinit var shell: ShellInterpreter

    private val historyBuffer = mutableListOf<String>()
    private var historyIndex = -1

    // ANSI colors
    private val colorMap = mapOf(
        "30" to Color.parseColor("#1a1a1a"),
        "31" to Color.parseColor("#FF5555"),
        "32" to Color.parseColor("#50FA7B"),
        "33" to Color.parseColor("#F1FA8C"),
        "34" to Color.parseColor("#6272A4"),
        "35" to Color.parseColor("#FF79C6"),
        "36" to Color.parseColor("#8BE9FD"),
        "37" to Color.parseColor("#F8F8F2"),
        "0"  to Color.parseColor("#F8F8F2")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vfs = VirtualFileSystem()
        shell = ShellInterpreter(vfs)

        setupUI()
        printBanner()
        printPrompt()
    }

    private fun setupUI() {
        // Click anywhere on output to focus input
        binding.outputScroll.setOnClickListener { focusInput() }
        binding.outputText.setOnClickListener { focusInput() }

        // Input field
        binding.inputField.typeface = Typeface.MONOSPACE
        binding.inputField.setTextColor(Color.parseColor("#F8F8F2"))
        binding.inputField.setHintTextColor(Color.parseColor("#6272A4"))
        binding.inputField.hint = "type command..."
        binding.inputField.background = null

        // Handle Enter key
        binding.inputField.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val cmd = binding.inputField.text.toString()
                handleCommand(cmd)
                true
            } else false
        }

        // Key listener for history navigation and Tab completion
        binding.inputField.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        navigateHistory(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        navigateHistory(1)
                        true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        tabComplete()
                        true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        val cmd = binding.inputField.text.toString()
                        handleCommand(cmd)
                        true
                    }
                    else -> false
                }
            } else false
        }

        // Quick command buttons
        binding.btnUp.setOnClickListener { navigateHistory(-1) }
        binding.btnDown.setOnClickListener { navigateHistory(1) }
        binding.btnTab.setOnClickListener { tabComplete() }
        binding.btnCtrlC.setOnClickListener { interruptCommand() }
        binding.btnClear.setOnClickListener {
            binding.outputText.text = ""
            printPrompt()
        }
        binding.btnPaste.setOnClickListener { pasteFromClipboard() }

        // Send button
        binding.btnSend.setOnClickListener {
            val cmd = binding.inputField.text.toString()
            handleCommand(cmd)
        }

        focusInput()
    }

    private fun focusInput() {
        binding.inputField.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.inputField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun printBanner() {
        val banner = """
╔══════════════════════════════════════════════╗
║         Linux Simulator for Android          ║
║         kernel 6.1.0-linuxsim  aarch64       ║
╚══════════════════════════════════════════════╝
Welcome! Type 'help' for available commands.
Type 'ls' to start exploring the filesystem.

""".trimStart()
        appendColoredOutput(banner, Color.parseColor("#50FA7B"))
    }

    private fun handleCommand(input: String) {
        val cmd = input.trim()

        // Echo command with prompt
        val promptLine = shell.getPrompt() + cmd + "\n"
        appendColoredOutput(promptLine, Color.parseColor("#8BE9FD"))

        binding.inputField.text.clear()
        historyBuffer.add(0, cmd)
        historyIndex = -1

        if (cmd.isEmpty()) {
            printPrompt()
            return
        }

        val result = shell.execute(cmd)

        when {
            result == "__EXIT__" -> {
                appendColoredOutput("logout\nSimulation ended. Restarting...\n", Color.parseColor("#FFB86C"))
                vfs = VirtualFileSystem()
                shell = ShellInterpreter(vfs)
                printBanner()
            }
            result.startsWith("\u001b[2J") -> {
                // Clear screen
                binding.outputText.text = ""
            }
            else -> {
                if (result.isNotEmpty()) {
                    appendAnsiOutput(result)
                }
            }
        }

        printPrompt()
        scrollToBottom()
    }

    private fun printPrompt() {
        val prompt = shell.getPrompt()
        appendColoredOutput(prompt, Color.parseColor("#50FA7B"))
        scrollToBottom()
    }

    private fun appendColoredOutput(text: String, color: Int) {
        val ssb = SpannableStringBuilder(text)
        ssb.setSpan(ForegroundColorSpan(color), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.outputText.append(ssb)
    }

    private fun appendAnsiOutput(text: String) {
        // Parse ANSI color codes
        val ansiRegex = Regex("\u001b\\[(\\d+)m")
        val ssb = SpannableStringBuilder()
        var currentColor = Color.parseColor("#F8F8F2")
        var lastIndex = 0

        for (match in ansiRegex.findAll(text)) {
            // Append text before this escape sequence
            if (match.range.first > lastIndex) {
                val segment = text.substring(lastIndex, match.range.first)
                val start = ssb.length
                ssb.append(segment)
                ssb.setSpan(ForegroundColorSpan(currentColor), start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Update color
            val code = match.groupValues[1]
            currentColor = colorMap[code] ?: Color.parseColor("#F8F8F2")
            lastIndex = match.range.last + 1
        }

        // Remaining text after last escape
        if (lastIndex < text.length) {
            val segment = text.substring(lastIndex)
            val start = ssb.length
            ssb.append(segment)
            ssb.setSpan(ForegroundColorSpan(currentColor), start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.outputText.append(ssb)
    }

    private fun navigateHistory(direction: Int) {
        if (historyBuffer.isEmpty()) return
        historyIndex = (historyIndex + direction).coerceIn(-1, historyBuffer.size - 1)
        binding.inputField.setText(if (historyIndex < 0) "" else historyBuffer[historyIndex])
        binding.inputField.setSelection(binding.inputField.text.length)
    }

    private fun tabComplete() {
        val current = binding.inputField.text.toString()
        val lastWord = current.split(" ").lastOrNull() ?: return
        val dir = vfs.currentDir()

        // Get completions
        val completions = dir.children.keys
            .filter { it.startsWith(lastWord) }
            .sorted()

        when {
            completions.isEmpty() -> return
            completions.size == 1 -> {
                val completed = current.dropLast(lastWord.length) + completions[0]
                val suffix = if (dir.children[completions[0]]?.isDirectory == true) "/" else " "
                binding.inputField.setText(completed + suffix)
                binding.inputField.setSelection(binding.inputField.text.length)
            }
            else -> {
                // Show completions
                appendColoredOutput("\n" + completions.joinToString("  ") + "\n", Color.parseColor("#6272A4"))
                appendColoredOutput(shell.getPrompt(), Color.parseColor("#50FA7B"))
                appendColoredOutput(current, Color.parseColor("#F8F8F2"))
                scrollToBottom()
            }
        }
    }

    private fun interruptCommand() {
        appendColoredOutput("^C\n", Color.parseColor("#FF5555"))
        binding.inputField.text.clear()
        historyIndex = -1
        printPrompt()
        scrollToBottom()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            binding.inputField.append(text)
        }
    }

    private fun scrollToBottom() {
        binding.outputScroll.post {
            binding.outputScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
