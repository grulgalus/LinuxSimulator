package com.linuxsim

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShellInterpreter(private val vfs: VirtualFileSystem) {

    private val username = "user"
    private val hostname = "android-linux"
    private val history = mutableListOf<String>()
    private val aliases = mutableMapOf(
        "ll" to "ls -la",
        "la" to "ls -A",
        "l" to "ls -CF"
    )
    private val envVars = mutableMapOf(
        "HOME" to "/home/user",
        "USER" to "user",
        "SHELL" to "/bin/bash",
        "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
        "PWD" to "/home/user",
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8"
    )
    private var lastExitCode = 0

    fun getPrompt(): String {
        val path = vfs.currentPathStr()
        val home = "/home/user"
        val display = if (path.startsWith(home)) "~" + path.removePrefix(home) else path
        return "$username@$hostname:$display\$ "
    }

    fun execute(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return ""
        history.add(input)

        // Resolve aliases
        val resolved = resolveAlias(input)

        // Handle pipes
        if (resolved.contains("|")) {
            return executePipe(resolved)
        }

        // Handle && and ||
        if (resolved.contains("&&")) {
            val parts = resolved.split("&&")
            var result = ""
            for (part in parts) {
                result = executeSingle(part.trim())
                if (lastExitCode != 0) break
            }
            return result
        }

        return executeSingle(resolved)
    }

    private fun resolveAlias(input: String): String {
        val cmd = input.split(" ").first()
        val rest = input.removePrefix(cmd)
        return (aliases[cmd] ?: cmd) + rest
    }

    private fun executePipe(input: String): String {
        val commands = input.split("|").map { it.trim() }
        var output = ""
        for (cmd in commands) {
            output = executeSingleWithStdin(cmd, output)
        }
        return output
    }

    private fun executeSingleWithStdin(cmd: String, stdin: String): String {
        val parts = parseArgs(cmd)
        if (parts.isEmpty()) return stdin
        return when (parts[0]) {
            "grep" -> cmdGrep(parts.drop(1), stdin)
            "wc" -> cmdWc(parts.drop(1), stdin)
            "sort" -> stdin.lines().filter { it.isNotEmpty() }.sorted().joinToString("\n")
            "uniq" -> stdin.lines().distinct().joinToString("\n")
            "head" -> {
                val n = if (parts.size > 2 && parts[1] == "-n") parts[2].toIntOrNull() ?: 10 else 10
                stdin.lines().take(n).joinToString("\n")
            }
            "tail" -> {
                val n = if (parts.size > 2 && parts[1] == "-n") parts[2].toIntOrNull() ?: 10 else 10
                stdin.lines().takeLast(n).joinToString("\n")
            }
            "tr" -> {
                if (parts.size >= 3) stdin.replace(parts[1], parts[2])
                else stdin
            }
            "sed" -> cmdSed(parts.drop(1), stdin)
            "awk" -> "awk: not fully supported in pipe mode\n"
            else -> executeSingle(cmd)
        }
    }

    private fun executeSingle(input: String): String {
        val parts = parseArgs(input)
        if (parts.isEmpty()) return ""

        // Variable substitution
        val cmd = parts[0]
        val args = parts.drop(1)

        return when (cmd) {
            "ls" -> cmdLs(args)
            "ll" -> cmdLs(listOf("-la") + args)
            "cd" -> cmdCd(args)
            "pwd" -> vfs.currentPathStr() + "\n"
            "cat" -> cmdCat(args)
            "echo" -> cmdEcho(args)
            "mkdir" -> cmdMkdir(args)
            "rmdir" -> cmdRmdir(args)
            "rm" -> cmdRm(args)
            "cp" -> cmdCp(args)
            "mv" -> cmdMv(args)
            "touch" -> cmdTouch(args)
            "find" -> cmdFind(args)
            "grep" -> cmdGrep(args, "")
            "wc" -> cmdWc(args, "")
            "head" -> cmdHead(args)
            "tail" -> cmdTail(args)
            "sort" -> cmdSort(args)
            "clear" -> "\u001b[2J\u001b[H" // will be handled by UI
            "help" -> cmdHelp()
            "man" -> cmdMan(args)
            "uname" -> cmdUname(args)
            "whoami" -> "$username\n"
            "id" -> "uid=1000($username) gid=1000($username) groups=1000($username),4(adm),27(sudo)\n"
            "hostname" -> "$hostname\n"
            "date" -> SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).format(Date()) + "\n"
            "uptime" -> " ${SimpleDateFormat("HH:mm", Locale.US).format(Date())} up 0:42,  1 user,  load average: 0.00, 0.00, 0.00\n"
            "ps" -> cmdPs(args)
            "top" -> cmdTop()
            "df" -> cmdDf(args)
            "du" -> cmdDu(args)
            "free" -> cmdFree(args)
            "env" -> envVars.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n"
            "export" -> cmdExport(args)
            "unset" -> cmdUnset(args)
            "printenv" -> cmdPrintenv(args)
            "alias" -> cmdAlias(args)
            "unalias" -> cmdUnalias(args)
            "history" -> history.mapIndexed { i, s -> "  ${i + 1}  $s" }.joinToString("\n") + "\n"
            "which" -> cmdWhich(args)
            "type" -> cmdType(args)
            "file" -> cmdFile(args)
            "stat" -> cmdStat(args)
            "chmod" -> cmdChmod(args)
            "chown" -> cmdChown(args)
            "ln" -> cmdLn(args)
            "readlink" -> cmdReadlink(args)
            "basename" -> if (args.isNotEmpty()) args[0].split("/").last() + "\n" else "basename: missing operand\n"
            "dirname" -> if (args.isNotEmpty()) args[0].substringBeforeLast("/").ifEmpty { "." } + "\n" else ".\n"
            "cut" -> cmdCut(args)
            "tr" -> cmdTr(args)
            "sed" -> cmdSed(args, "")
            "tee" -> cmdTee(args)
            "printf" -> cmdPrintf(args)
            "sleep" -> "sleep: simulated (no actual delay)\n"
            "ping" -> cmdPing(args)
            "curl" -> "curl: network access not available in simulation\n"
            "wget" -> "wget: network access not available in simulation\n"
            "ssh" -> "ssh: simulation mode – network disabled\n"
            "tar" -> cmdTar(args)
            "zip", "unzip" -> "zip/unzip: not available in simulation\n"
            "diff" -> cmdDiff(args)
            "less", "more" -> cmdCat(args)
            "nano", "vi", "vim", "emacs" -> "Editor simulation – use 'cat > file' to write files\n"
            "python3", "python" -> "Python 3.11.0 (simulated)\nType 'exit()' to quit.\n>>> "
            "bash", "sh" -> "bash: spawning subshell not supported\n"
            "exit", "logout" -> "__EXIT__"
            "reboot", "shutdown" -> "Simulating $cmd...\nSystem going down NOW!\n"
            "apt", "apt-get" -> cmdApt(args)
            "dpkg" -> "dpkg: package management simulated\n"
            "pacman" -> cmdPacman(args)
            "systemctl" -> cmdSystemctl(args)
            "journalctl" -> "-- Journal begins --\n" + (vfs.resolve("var/log/syslog")?.content ?: "")
            "crontab" -> "crontab: ${args.joinToString(" ")} simulated\n"
            "lsblk" -> cmdLsblk()
            "lscpu" -> cmdLscpu()
            "lsusb" -> "Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub\n"
            "lspci" -> "00:00.0 Host bridge: Simulated bridge\n00:01.0 VGA compatible controller: Simulated GPU\n"
            "ifconfig", "ip" -> cmdIfconfig()
            "netstat" -> "Active Internet connections (only servers)\nProto Recv-Q Send-Q Local Address\n"
            "ss" -> "Netid  State   Recv-Q  Send-Q  Local Address:Port\n"
            "mount" -> "sysfs on /sys type sysfs\ntmpfs on /tmp type tmpfs\n"
            "umount" -> "umount: simulated\n"
            "dmesg" -> cmdDmesg()
            "strace" -> "strace: not available in simulation\n"
            "ldd" -> "ldd: not available in simulation\n"
            "strings" -> "strings: not available in simulation\n"
            "xxd", "hexdump" -> "hexdump: not available in simulation\n"
            "bc" -> "bc: basic calculator – use echo $((expression))\n"
            "expr" -> cmdExpr(args)
            "test", "[" -> cmdTest(args)
            "true" -> { lastExitCode = 0; "" }
            "false" -> { lastExitCode = 1; "" }
            "yes" -> "y\ny\ny\n[...]\n(yes: simulated, output truncated)\n"
            "seq" -> cmdSeq(args)
            "factor" -> cmdFactor(args)
            "cal" -> cmdCal()
            "time" -> "time: not available in simulation\n"
            else -> {
                // Check if it's an assignment (VAR=value)
                if (cmd.contains("=") && !cmd.startsWith("-")) {
                    val (k, v) = cmd.split("=", limit = 2)
                    envVars[k] = v
                    ""
                } else {
                    lastExitCode = 127
                    "-bash: $cmd: command not found\n"
                }
            }
        }
    }

    // ─── Commands ─────────────────────────────────────────────────────────────

    private fun cmdLs(args: List<String>): String {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val paths = args.filter { !it.startsWith("-") }
        val showAll = 'a' in flags || 'A' in flags
        val longFormat = 'l' in flags
        val humanReadable = 'h' in flags

        val targetPath = paths.firstOrNull() ?: ""
        val target = if (targetPath.isEmpty()) vfs.currentDir()
        else vfs.resolve(targetPath) ?: return "ls: cannot access '$targetPath': No such file or directory\n"

        if (!target.isDirectory) {
            return if (longFormat) formatLong(target, humanReadable) + "\n" else target.name + "\n"
        }

        val entries = target.children.values
            .filter { showAll || !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        if (entries.isEmpty()) return ""

        return if (longFormat) {
            val total = entries.size * 4
            "total $total\n" + entries.joinToString("\n") { formatLong(it, humanReadable) } + "\n"
        } else {
            entries.chunked(4).joinToString("\n") { row ->
                row.joinToString("  ") { node ->
                    val name = if (node.isDirectory) "\u001b[34m${node.name}\u001b[0m" else node.name
                    name.padEnd(20)
                }
            } + "\n"
        }
    }

    private fun formatLong(node: VFSNode, human: Boolean): String {
        val size = if (human) formatSize(node.content.length.toLong()) else node.content.length.toString()
        val date = "May 17 10:00"
        val name = if (node.isDirectory) "\u001b[34m${node.name}\u001b[0m" else node.name
        val link = if (node.isDirectory) "2" else "1"
        return "${node.permissions} $link ${node.owner} ${node.group} ${size.padStart(8)} $date $name"
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}K"
            else -> "${bytes / (1024 * 1024)}M"
        }
    }

    private fun cmdCd(args: List<String>): String {
        val path = args.firstOrNull() ?: "/home/user"
        val target = if (path == "~") "/home/user" else path
        val parts = vfs.resolveParts(target) ?: return "cd: $target: No such file or directory\n"
        var node = vfs.root
        for (p in parts) {
            val child = node.children[p] ?: return "cd: $target: No such file or directory\n"
            if (!child.isDirectory) return "cd: $target: Not a directory\n"
            node = child
        }
        vfs.currentPath = parts
        envVars["PWD"] = vfs.currentPathStr()
        lastExitCode = 0
        return ""
    }

    private fun cmdCat(args: List<String>): String {
        if (args.isEmpty()) return "cat: reading from stdin not supported in simulation\n"
        val sb = StringBuilder()
        for (arg in args) {
            val node = vfs.resolve(arg) ?: return "cat: $arg: No such file or directory\n"
            if (node.isDirectory) return "cat: $arg: Is a directory\n"
            sb.append(node.content)
        }
        return sb.toString()
    }

    private fun cmdEcho(args: List<String>): String {
        val noNewline = args.firstOrNull() == "-n"
        val text = (if (noNewline) args.drop(1) else args).joinToString(" ")
            .replace("\\n", "\n").replace("\\t", "\t")
        // Expand $VAR
        val expanded = expandVars(text)
        return if (noNewline) expanded else expanded + "\n"
    }

    private fun expandVars(s: String): String {
        var result = s
        envVars.forEach { (k, v) -> result = result.replace("\$$k", v) }
        result = result.replace("\$?", lastExitCode.toString())
        result = result.replace("\$\$", android.os.Process.myPid().toString())
        return result
    }

    private fun cmdMkdir(args: List<String>): String {
        val parents = "-p" in args
        val dirs = args.filter { !it.startsWith("-") }
        if (dirs.isEmpty()) return "mkdir: missing operand\n"
        for (dir in dirs) {
            val parts = vfs.resolveParts(dir) ?: continue
            if (!parents) {
                val parent = vfs.parentOf(parts) ?: return "mkdir: cannot create directory '$dir': No such file or directory\n"
                val name = parts.last()
                if (parent.children.containsKey(name)) return "mkdir: cannot create directory '$dir': File exists\n"
                parent.children[name] = VFSNode(name, true)
            } else {
                var node = vfs.root
                for (p in parts) {
                    node = node.children.getOrPut(p) { VFSNode(p, true) }
                }
            }
        }
        lastExitCode = 0
        return ""
    }

    private fun cmdRmdir(args: List<String>): String {
        val dirs = args.filter { !it.startsWith("-") }
        for (dir in dirs) {
            val node = vfs.resolve(dir) ?: return "rmdir: failed to remove '$dir': No such file or directory\n"
            if (!node.isDirectory) return "rmdir: failed to remove '$dir': Not a directory\n"
            if (node.children.isNotEmpty()) return "rmdir: failed to remove '$dir': Directory not empty\n"
            vfs.deleteNode(dir)
        }
        return ""
    }

    private fun cmdRm(args: List<String>): String {
        val recursive = "-r" in args || "-rf" in args || "-fr" in args
        val force = "-f" in args || "-rf" in args
        val files = args.filter { !it.startsWith("-") }
        if (files.isEmpty()) return "rm: missing operand\n"
        for (file in files) {
            val node = vfs.resolve(file)
            if (node == null) {
                if (!force) return "rm: cannot remove '$file': No such file or directory\n"
                continue
            }
            if (node.isDirectory && !recursive) return "rm: cannot remove '$file': Is a directory\n"
            vfs.deleteNode(file)
        }
        return ""
    }

    private fun cmdCp(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") }
        if (files.size < 2) return "cp: missing destination\n"
        val src = files[files.size - 2]
        val dst = files[files.size - 1]
        val srcNode = vfs.resolve(src) ?: return "cp: cannot stat '$src': No such file or directory\n"
        val dstNode = vfs.resolve(dst)
        val destPath = if (dstNode?.isDirectory == true) "$dst/${srcNode.name}" else dst
        val newNode = vfs.getOrCreateNode(destPath, false) ?: return "cp: cannot create '$dst'\n"
        newNode.content.let { /* copy */ }
        val parts = vfs.resolveParts(destPath)!!
        val parent = vfs.parentOf(parts)!!
        parent.children[parts.last()] = VFSNode(parts.last(), srcNode.isDirectory, content = srcNode.content)
        return ""
    }

    private fun cmdMv(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") }
        if (files.size < 2) return "mv: missing destination\n"
        val src = files[0]
        val dst = files[1]
        val srcNode = vfs.resolve(src) ?: return "mv: cannot stat '$src': No such file or directory\n"
        val dstNode = vfs.resolve(dst)
        val destPath = if (dstNode?.isDirectory == true) "$dst/${srcNode.name}" else dst
        val parts = vfs.resolveParts(destPath)!!
        val parent = vfs.parentOf(parts) ?: return "mv: cannot move to '$dst'\n"
        parent.children[parts.last()] = VFSNode(parts.last(), srcNode.isDirectory,
            children = srcNode.children, content = srcNode.content)
        vfs.deleteNode(src)
        return ""
    }

    private fun cmdTouch(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") }
        if (files.isEmpty()) return "touch: missing file operand\n"
        for (file in files) {
            val existing = vfs.resolve(file)
            if (existing == null) {
                vfs.getOrCreateNode(file, false)
            }
        }
        return ""
    }

    private fun cmdFind(args: List<String>): String {
        val path = args.firstOrNull()?.takeIf { !it.startsWith("-") } ?: "."
        val nameFilter = args.indexOf("-name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        val typeFilter = args.indexOf("-type").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        val root = vfs.resolve(path) ?: return "find: '$path': No such file or directory\n"
        val results = StringBuilder()
        findRecursive(root, path.trimEnd('/'), nameFilter, typeFilter, results)
        return results.toString()
    }

    private fun findRecursive(node: VFSNode, path: String, nameFilter: String?, typeFilter: String?, sb: StringBuilder) {
        val matchType = typeFilter == null || (typeFilter == "d" && node.isDirectory) || (typeFilter == "f" && !node.isDirectory)
        val matchName = nameFilter == null || matchGlob(node.name, nameFilter)
        if (matchType && matchName) sb.appendLine(path)
        if (node.isDirectory) {
            for (child in node.children.values) {
                findRecursive(child, "$path/${child.name}", nameFilter, typeFilter, sb)
            }
        }
    }

    private fun matchGlob(name: String, pattern: String): Boolean {
        val regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        return name.matches(Regex(regex))
    }

    private fun cmdGrep(args: List<String>, stdin: String): String {
        val flags = args.filter { it.startsWith("-") }
        val posArgs = args.filter { !it.startsWith("-") }
        val ignoreCase = "-i" in flags
        val invertMatch = "-v" in flags
        val showLineNum = "-n" in flags
        val recursive = "-r" in flags || "-R" in flags

        if (posArgs.isEmpty()) return "grep: missing pattern\n"
        val pattern = posArgs[0]
        val files = posArgs.drop(1)

        val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)

        val input = if (files.isEmpty()) stdin
        else files.joinToString("") { f -> vfs.resolve(f)?.content ?: "" }

        return input.lines().mapIndexedNotNull { i, line ->
            val matches = regex.containsMatchIn(line)
            if (matches xor invertMatch) {
                if (showLineNum) "${i + 1}:$line" else line
            } else null
        }.joinToString("\n").let { if (it.isNotEmpty()) it + "\n" else "" }
    }

    private fun cmdWc(args: List<String>, stdin: String): String {
        val flags = args.filter { it.startsWith("-") }
        val files = args.filter { !it.startsWith("-") }
        val input = if (files.isEmpty()) stdin
        else files.joinToString("") { vfs.resolve(it)?.content ?: "" }
        val lines = input.lines().size
        val words = input.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val bytes = input.length
        val showLines = "-l" in flags || flags.isEmpty()
        val showWords = "-w" in flags || flags.isEmpty()
        val showBytes = "-c" in flags || flags.isEmpty()
        val parts = mutableListOf<String>()
        if (showLines) parts.add(lines.toString().padStart(7))
        if (showWords) parts.add(words.toString().padStart(7))
        if (showBytes) parts.add(bytes.toString().padStart(7))
        return parts.joinToString(" ") + "\n"
    }

    private fun cmdHead(args: List<String>): String {
        val n = if ("-n" in args) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
        val files = args.filter { !it.startsWith("-") && it != args.getOrNull(args.indexOf("-n") + 1) }
        if (files.isEmpty()) return "head: stdin not supported\n"
        return files.joinToString("") { f ->
            val content = vfs.resolve(f)?.content ?: return "head: $f: No such file or directory\n"
            content.lines().take(n).joinToString("\n") + "\n"
        }
    }

    private fun cmdTail(args: List<String>): String {
        val n = if ("-n" in args) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
        val files = args.filter { !it.startsWith("-") && it != args.getOrNull(args.indexOf("-n") + 1) }
        if (files.isEmpty()) return "tail: stdin not supported\n"
        return files.joinToString("") { f ->
            val content = vfs.resolve(f)?.content ?: return "tail: $f: No such file or directory\n"
            content.lines().takeLast(n).joinToString("\n") + "\n"
        }
    }

    private fun cmdSort(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") }
        if (files.isEmpty()) return "sort: stdin not supported\n"
        val reverse = "-r" in args
        val numeric = "-n" in args
        return files.joinToString("") { f ->
            val content = vfs.resolve(f)?.content ?: return "sort: $f: No such file or directory\n"
            val lines = content.lines().filter { it.isNotEmpty() }
            val sorted = if (numeric) lines.sortedBy { it.trimStart().toDoubleOrNull() ?: 0.0 }
            else lines.sorted()
            (if (reverse) sorted.reversed() else sorted).joinToString("\n") + "\n"
        }
    }

    private fun cmdSed(args: List<String>, stdin: String): String {
        val expr = args.firstOrNull() ?: return "sed: missing expression\n"
        val files = args.drop(1)
        val input = if (files.isEmpty()) stdin
        else files.joinToString("") { vfs.resolve(it)?.content ?: "" }

        // Simple s/pattern/replacement/flags
        val match = Regex("^s/(.+?)/(.*)/(g?)$").find(expr)
        return if (match != null) {
            val pattern = match.groupValues[1]
            val replacement = match.groupValues[2]
            val global = match.groupValues[3] == "g"
            if (global) input.replace(Regex(pattern), replacement)
            else input.lines().joinToString("\n") { it.replaceFirst(Regex(pattern), replacement) } + "\n"
        } else {
            "sed: unsupported expression: $expr\n"
        }
    }

    private fun cmdTee(args: List<String>): String {
        return "tee: requires stdin (pipe usage) – not supported standalone\n"
    }

    private fun cmdPrintf(args: List<String>): String {
        if (args.isEmpty()) return ""
        return args.joinToString(" ").replace("\\n", "\n").replace("\\t", "\t") + ""
    }

    private fun cmdCut(args: List<String>): String {
        return "cut: basic implementation – use grep/sed for complex parsing\n"
    }

    private fun cmdTr(args: List<String>): String {
        return "tr: use in pipe: echo 'text' | tr 'a' 'b'\n"
    }

    private fun cmdPs(args: List<String>): String {
        return """
PID   TTY      TIME     CMD
  1   pts/0    00:00:00 systemd
100   pts/0    00:00:00 bash
101   pts/0    00:00:00 linuxsim
200   pts/0    00:00:01 android
""".trimStart()
    }

    private fun cmdTop(): String {
        val mem = Runtime.getRuntime()
        val used = (mem.totalMemory() - mem.freeMemory()) / 1024 / 1024
        val total = mem.totalMemory() / 1024 / 1024
        return """
top - ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} up 0:42,  1 user,  load average: 0.00, 0.00, 0.00
Tasks:   4 total,   1 running,   3 sleeping
%Cpu(s):  2.0 us,  1.0 sy,  0.0 ni, 97.0 id
MiB Mem : ${total.toString().padStart(8)}.0 total, ${(total - used).toString().padStart(8)}.0 free, ${used.toString().padStart(8)}.0 used
MiB Swap:  1024.0 total,  1024.0 free,    0.0 used

  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
    1 root      20   0   33544   7216   6272 S   0.0   0.2   0:00.42 systemd
  100 user      20   0   24048   5680   4864 R   2.0   0.1   0:01.23 bash
  101 user      20   0   16384   4096   3072 S   0.0   0.1   0:00.10 linuxsim
""".trimStart()
    }

    private fun cmdDf(args: List<String>): String {
        val human = "-h" in args
        return if (human) {
            """
Filesystem      Size  Used Avail Use% Mounted on
/dev/sda1        32G  8.5G   22G  28% /
tmpfs           2.0G  1.2M  2.0G   1% /dev/shm
tmpfs           512M  8.0K  512M   1% /run
""".trimStart()
        } else {
            """
Filesystem     1K-blocks    Used Available Use% Mounted on
/dev/sda1       33554432 8912896  23592448  28% /
tmpfs            2097152    1200   2095952   1% /dev/shm
tmpfs             524288       8    524280   1% /run
""".trimStart()
        }
    }

    private fun cmdDu(args: List<String>): String {
        val human = "-h" in args
        val path = args.filter { !it.startsWith("-") }.firstOrNull() ?: "."
        return if (human) "4.0K\t$path\n" else "4\t$path\n"
    }

    private fun cmdFree(args: List<String>): String {
        val human = "-h" in args
        val mem = Runtime.getRuntime()
        val totalMB = mem.totalMemory() / 1024 / 1024
        val usedMB = (mem.totalMemory() - mem.freeMemory()) / 1024 / 1024
        val freeMB = totalMB - usedMB
        return if (human) {
            "               total        used        free      shared  buff/cache   available\n" +
            "Mem:         ${totalMB}Mi     ${usedMB}Mi    ${freeMB}Mi      1.0Mi     128Mi    ${freeMB + 128}Mi\n" +
            "Swap:        1024Mi         0Mi    1024Mi\n"
        } else {
            "               total        used        free      shared  buff/cache   available\n" +
            "Mem:         ${totalMB * 1024}      ${usedMB * 1024}    ${freeMB * 1024}       1024      131072    ${(freeMB + 128) * 1024}\n" +
            "Swap:        1048576           0     1048576\n"
        }
    }

    private fun cmdExport(args: List<String>): String {
        if (args.isEmpty()) return envVars.entries.joinToString("\n") { "declare -x ${it.key}=\"${it.value}\"" } + "\n"
        for (arg in args) {
            if (arg.contains("=")) {
                val (k, v) = arg.split("=", limit = 2)
                envVars[k] = v
            }
        }
        return ""
    }

    private fun cmdUnset(args: List<String>): String {
        args.forEach { envVars.remove(it) }
        return ""
    }

    private fun cmdPrintenv(args: List<String>): String {
        if (args.isEmpty()) return envVars.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n"
        return args.joinToString("\n") { envVars[it] ?: "" } + "\n"
    }

    private fun cmdAlias(args: List<String>): String {
        if (args.isEmpty()) return aliases.entries.joinToString("\n") { "alias ${it.key}='${it.value}'" } + "\n"
        for (arg in args) {
            if (arg.contains("=")) {
                val (k, v) = arg.split("=", limit = 2)
                aliases[k] = v.trim('\'', '"')
            }
        }
        return ""
    }

    private fun cmdUnalias(args: List<String>): String {
        args.forEach { aliases.remove(it) }
        return ""
    }

    private fun cmdWhich(args: List<String>): String {
        val builtins = setOf("ls", "cd", "cat", "echo", "mkdir", "rm", "cp", "mv", "pwd", "touch",
            "find", "grep", "wc", "head", "tail", "sort", "history", "help", "uname", "date",
            "ps", "top", "df", "du", "free", "env", "export", "alias", "clear")
        return args.joinToString("\n") { cmd ->
            if (cmd in builtins) "/usr/bin/$cmd" else "which: no $cmd in PATH"
        } + "\n"
    }

    private fun cmdType(args: List<String>): String {
        return args.joinToString("\n") { cmd ->
            if (aliases.containsKey(cmd)) "$cmd is aliased to '${aliases[cmd]}'"
            else "$cmd is a shell builtin"
        } + "\n"
    }

    private fun cmdFile(args: List<String>): String {
        return args.joinToString("\n") { path ->
            val node = vfs.resolve(path)
            when {
                node == null -> "$path: ERROR: cannot open"
                node.isDirectory -> "$path: directory"
                node.content.startsWith("#!/") -> "$path: Bourne-Again shell script, ASCII text executable"
                else -> "$path: ASCII text"
            }
        } + "\n"
    }

    private fun cmdStat(args: List<String>): String {
        val path = args.firstOrNull() ?: return "stat: missing operand\n"
        val node = vfs.resolve(path) ?: return "stat: cannot stat '$path': No such file or directory\n"
        return """
  File: $path
  Size: ${node.content.length}\t\tBlocks: 8\tIO Block: 4096   ${if (node.isDirectory) "directory" else "regular file"}
Device: fd01h/64769d\tInode: ${node.name.hashCode().and(0xFFFFF)}\tLinks: 1
Access: ${node.permissions}  Uid: ( 1000/    user)   Gid: ( 1000/    user)
Access: 2026-05-17 10:00:00.000000000 +0000
Modify: 2026-05-17 10:00:00.000000000 +0000
Change: 2026-05-17 10:00:00.000000000 +0000
 Birth: -
""".trimStart()
    }

    private fun cmdChmod(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") && it.length > 3 }
        val mode = args.filter { !it.startsWith("-") }.firstOrNull() ?: return "chmod: missing operand\n"
        files.forEach { path ->
            val node = vfs.resolve(path)
            // In a real implementation we'd update permissions
        }
        return ""
    }

    private fun cmdChown(args: List<String>): String = "" // simulated success

    private fun cmdLn(args: List<String>): String = "ln: symbolic links simulated but not traversed\n"

    private fun cmdReadlink(args: List<String>): String = "readlink: not a symbolic link\n"

    private fun cmdPing(args: List<String>): String {
        val host = args.filter { !it.startsWith("-") }.firstOrNull() ?: return "ping: missing host\n"
        return "PING $host (127.0.0.1) 56(84) bytes of data.\n" +
               "64 bytes from $host (127.0.0.1): icmp_seq=1 ttl=64 time=0.042 ms\n" +
               "(network access disabled in simulation)\n"
    }

    private fun cmdTar(args: List<String>): String = "tar: archive operations simulated (no actual files created)\n"

    private fun cmdDiff(args: List<String>): String {
        val files = args.filter { !it.startsWith("-") }
        if (files.size < 2) return "diff: missing operand\n"
        val a = vfs.resolve(files[0])?.content?.lines() ?: return "diff: ${files[0]}: No such file\n"
        val b = vfs.resolve(files[1])?.content?.lines() ?: return "diff: ${files[1]}: No such file\n"
        if (a == b) return ""
        val sb = StringBuilder()
        a.forEachIndexed { i, line -> if (i >= b.size || line != b[i]) sb.appendLine("< $line") }
        b.forEachIndexed { i, line -> if (i >= a.size || line != a[i]) sb.appendLine("> $line") }
        return if (sb.isEmpty()) "" else "--- ${files[0]}\n+++ ${files[1]}\n$sb"
    }

    private fun cmdExpr(args: List<String>): String {
        return try {
            val expr = args.joinToString(" ")
            when {
                expr.contains("+") -> (args[0].toLong() + args[2].toLong()).toString() + "\n"
                expr.contains("-") -> (args[0].toLong() - args[2].toLong()).toString() + "\n"
                expr.contains("*") -> (args[0].toLong() * args[2].toLong()).toString() + "\n"
                expr.contains("/") -> (args[0].toLong() / args[2].toLong()).toString() + "\n"
                expr.contains("%") -> (args[0].toLong() % args[2].toLong()).toString() + "\n"
                else -> "0\n"
            }
        } catch (e: Exception) { "expr: invalid expression\n" }
    }

    private fun cmdTest(args: List<String>): String {
        lastExitCode = 1
        return ""
    }

    private fun cmdSeq(args: List<String>): String {
        return try {
            when (args.size) {
                1 -> (1..args[0].toInt()).joinToString("\n") + "\n"
                2 -> (args[0].toInt()..args[1].toInt()).joinToString("\n") + "\n"
                3 -> {
                    val step = args[1].toInt()
                    (args[0].toInt()..args[2].toInt() step step).joinToString("\n") + "\n"
                }
                else -> "seq: missing operand\n"
            }
        } catch (e: Exception) { "seq: invalid argument\n" }
    }

    private fun cmdFactor(args: List<String>): String {
        return args.joinToString("\n") { n ->
            try {
                var num = n.toLong()
                val factors = mutableListOf<Long>()
                var d = 2L
                while (d * d <= num) {
                    while (num % d == 0L) { factors.add(d); num /= d }
                    d++
                }
                if (num > 1) factors.add(num)
                "$n: ${factors.joinToString(" ")}"
            } catch (e: Exception) { "factor: '$n' is not a valid number" }
        } + "\n"
    }

    private fun cmdCal(): String {
        val cal = java.util.Calendar.getInstance()
        val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, Locale.US)
        val year = cal.get(java.util.Calendar.YEAR)
        return "   $month $year\nSu Mo Tu We Th Fr Sa\n" +
               " 1  2  3  4  5  6  7\n" +
               " 8  9 10 11 12 13 14\n" +
               "15 16 17 18 19 20 21\n" +
               "22 23 24 25 26 27 28\n" +
               "29 30 31\n"
    }

    private fun cmdUname(args: List<String>): String {
        val all = "-a" in args
        val kernel = "-r" in args || "-v" in args
        return when {
            all -> "Linux android-linux 6.1.0-linuxsim #1 SMP PREEMPT_DYNAMIC ARM aarch64 GNU/Linux\n"
            kernel -> "6.1.0-linuxsim\n"
            "-m" in args -> "aarch64\n"
            "-s" in args -> "Linux\n"
            "-n" in args -> hostname + "\n"
            else -> "Linux\n"
        }
    }

    private fun cmdLsblk(): String = """
NAME   MAJ:MIN RM  SIZE RO TYPE MOUNTPOINT
sda      8:0    0   32G  0 disk
└─sda1   8:1    0   32G  0 part /
""".trimStart()

    private fun cmdLscpu(): String = """
Architecture:            aarch64
CPU op-mode(s):          32-bit, 64-bit
Byte Order:              Little Endian
CPU(s):                  8
On-line CPU(s) list:     0-7
Model name:              ARM Cortex-A55
CPU MHz:                 2000.000
BogoMIPS:                48.00
L1d cache:               64 KiB
L1i cache:               64 KiB
L2 cache:                512 KiB
L3 cache:                4 MiB
""".trimStart()

    private fun cmdIfconfig(): String = """
eth0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
        inet 192.168.1.100  netmask 255.255.255.0  broadcast 192.168.1.255
        ether 02:42:ac:11:00:02  txqueuelen 0  (Ethernet)
        RX packets 12345  bytes 1234567 (1.2 MB)
        TX packets 9876   bytes 987654 (987.6 KB)

lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536
        inet 127.0.0.1  netmask 255.0.0.0
        loop  txqueuelen 1000  (Local Loopback)
""".trimStart()

    private fun cmdDmesg(): String = """
[    0.000000] Linux version 6.1.0-linuxsim
[    0.000000] Kernel command line: root=/dev/sda1 ro quiet splash
[    0.100000] ACPI: IRQ0 used by override.
[    0.200000] PCI: Using configuration type 1 for base access
[    1.000000] NET: Registered PF_INET protocol family
[    1.500000] EXT4-fs (sda1): mounted filesystem
[    2.000000] systemd[1]: Reached target Graphical Interface.
""".trimStart()

    private fun cmdApt(args: List<String>): String {
        val sub = args.firstOrNull() ?: return "apt: usage: apt [install|remove|update|upgrade|search] package\n"
        val pkg = args.drop(1).firstOrNull() ?: ""
        return when (sub) {
            "install" -> "Reading package lists... Done\nBuilding dependency tree... Done\nSimulating install of '$pkg'... Done\n"
            "remove" -> "Simulating removal of '$pkg'... Done\n"
            "update" -> "Hit:1 http://archive.ubuntu.com/ubuntu jammy InRelease\nReading package lists... Done\n"
            "upgrade" -> "Reading package lists... Done\n0 upgraded, 0 newly installed, 0 to remove.\n"
            "search" -> "Searching for '$pkg'...\n${pkg}-utils - Utilities for $pkg\nlib${pkg}-dev - Development files for $pkg\n"
            "list" -> "Listing installed packages (simulated)...\nbash/jammy 5.1.16 amd64\ncoreutils/jammy 8.32 amd64\ncurl/jammy 7.81.0 amd64\n"
            else -> "apt: invalid operation $sub\n"
        }
    }

    private fun cmdPacman(args: List<String>): String {
        val flag = args.firstOrNull() ?: return "pacman: usage: pacman -S/-R/-Syu package\n"
        val pkg = args.drop(1).firstOrNull() ?: ""
        return when (flag) {
            "-S" -> "resolving dependencies...\nlooking for conflicting packages...\nSimulating install of $pkg... done\n"
            "-Syu" -> ":: Synchronizing package databases...\n:: Starting full system upgrade...\nthere is nothing to do\n"
            "-R" -> "Simulating removal of $pkg... done\n"
            "-Ss" -> "community/$pkg 1.0.0-1\n    A simulated package\n"
            else -> "pacman: unknown flag $flag\n"
        }
    }

    private fun cmdSystemctl(args: List<String>): String {
        val action = args.firstOrNull() ?: return "systemctl: action required\n"
        val service = args.drop(1).firstOrNull() ?: ""
        return when (action) {
            "status" -> "● $service.service - Linux Simulator $service\n   Loaded: loaded (/lib/systemd/system/$service.service)\n   Active: active (running) since May 17 10:00:00\n"
            "start" -> "Starting $service... done\n"
            "stop" -> "Stopping $service... done\n"
            "restart" -> "Restarting $service... done\n"
            "enable" -> "Created symlink /etc/systemd/system/$service.service\n"
            "disable" -> "Removed /etc/systemd/system/$service.service\n"
            "list-units" -> "UNIT\t\t\t\tLOAD\tACTIVE\tSUB\tDESCRIPTION\nsystemd-journald.service\tloaded\tactive\trunning\tJournal Service\nnginx.service\t\t\tloaded\tactive\trunning\tA High Performance HTTP Server\n"
            else -> "systemctl: unknown command '$action'\n"
        }
    }

    private fun cmdMan(args: List<String>): String {
        val cmd = args.firstOrNull() ?: return "What manual page do you want?\n"
        return "MAN(1) - Manual page for $cmd\n\nNAME\n       $cmd - simulated command\n\nSYNOPSIS\n       $cmd [OPTIONS] [ARGUMENTS]\n\nDESCRIPTION\n       Linux Simulator implementation of $cmd.\n       Type 'help' for the full command list.\n\n"
    }

    private fun cmdHelp(): String = """
╔════════════════════════════════════════════════════════════╗
║              Linux Simulator - Available Commands           ║
╚════════════════════════════════════════════════════════════╝

FILE SYSTEM
  ls [-la]       List directory contents
  cd [dir]       Change directory
  pwd            Print working directory
  mkdir [-p]     Make directories
  rmdir          Remove empty directory
  rm [-rf]       Remove files/directories
  cp             Copy files
  mv             Move/rename files
  touch          Create file or update timestamp
  cat            Display file contents
  find           Search for files
  stat           Display file status
  file           Determine file type

TEXT PROCESSING
  echo           Display text
  grep [-inv]    Search text patterns
  wc [-lwc]      Word/line/byte count
  head/tail [-n] First/last N lines
  sort [-rn]     Sort lines
  sed            Stream editor (s/pat/rep/g)
  diff           Compare files
  cut/tr/printf  Text manipulation

SYSTEM INFO
  uname [-a]     System information
  whoami / id    User identity
  hostname       Show hostname
  date           Current date/time
  uptime         System uptime
  ps             Process list
  top            System monitor
  df [-h]        Disk usage
  du [-h]        Directory size
  free [-h]      Memory usage
  lscpu          CPU information
  lsblk          Block devices
  lsusb/lspci    USB/PCI devices
  dmesg          Kernel messages
  ifconfig/ip    Network interfaces
  ping           Network test (simulated)

SHELL
  alias          List/set aliases
  unalias        Remove aliases
  export         Set env variables
  env/printenv   Show environment
  history        Command history
  which/type     Find commands
  expr/seq       Arithmetic/sequences
  factor/cal     Math utilities
  clear          Clear terminal

PACKAGE MANAGEMENT
  apt install/remove/update/search
  pacman -S/-R/-Syu/-Ss
  systemctl start/stop/status/enable

Type 'man <command>' for more details.
"""

    // ─── Argument parser ──────────────────────────────────────────────────────

    private fun parseArgs(input: String): List<String> {
        val args = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                c == ' ' && !inSingle && !inDouble -> {
                    if (sb.isNotEmpty()) { args.add(sb.toString()); sb.clear() }
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) args.add(sb.toString())
        return args
    }

    private fun executePipe(input: String): String {
        val commands = input.split("|").map { it.trim() }
        var output = ""
        for (cmd in commands) {
            output = executeSingleWithStdin(cmd, output)
        }
        return output
    }
}
