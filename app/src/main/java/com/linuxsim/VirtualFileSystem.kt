package com.linuxsim

import java.util.TreeMap

data class VFSNode(
    val name: String,
    val isDirectory: Boolean,
    val children: TreeMap<String, VFSNode> = TreeMap(),
    var content: String = "",
    var permissions: String = if (isDirectory) "drwxr-xr-x" else "-rw-r--r--",
    val owner: String = "user",
    val group: String = "user"
)

class VirtualFileSystem {

    val root = VFSNode("/", true)
    var currentPath = mutableListOf<String>() // empty = root

    init {
        buildDefaultFilesystem()
    }

    private fun buildDefaultFilesystem() {
        // Standard Linux dirs
        val dirs = listOf(
            "home/user/Documents",
            "home/user/Downloads",
            "home/user/Desktop",
            "home/user/.config",
            "home/user/.local/share",
            "etc",
            "bin",
            "usr/bin",
            "usr/lib",
            "usr/share",
            "var/log",
            "var/tmp",
            "tmp",
            "dev",
            "proc",
            "sys",
            "boot",
            "lib",
            "opt",
            "root",
            "sbin",
            "srv",
            "mnt",
            "media",
            "run"
        )
        dirs.forEach { createPath(it, true) }

        // Default files
        createFile("etc/hostname", "android-linux\n")
        createFile("etc/os-release",
            "NAME=\"Linux Simulator\"\nVERSION=\"1.0\"\nID=linuxsim\nPRETTY_NAME=\"Linux Simulator 1.0\"\n")
        createFile("etc/passwd",
            "root:x:0:0:root:/root:/bin/bash\nuser:x:1000:1000:User,,,:/home/user:/bin/bash\n")
        createFile("etc/hosts",
            "127.0.0.1\tlocalhost\n127.0.1.1\tandroid-linux\n::1\t\tlocalhost\n")
        createFile("etc/fstab",
            "# /etc/fstab\ntmpfs\t/tmp\ttmpfs\tdefaults\t0 0\n")
        createFile("home/user/.bashrc",
            "# ~/.bashrc\nexport PATH=/usr/bin:/bin:/usr/sbin:/sbin\nalias ll='ls -la'\nalias la='ls -A'\nexport PS1='\\u@\\h:\\w\\$ '\n")
        createFile("home/user/.profile",
            "# ~/.profile\nif [ -f ~/.bashrc ]; then\n  . ~/.bashrc\nfi\n")
        createFile("home/user/Documents/readme.txt",
            "Welcome to Linux Simulator!\n\nThis is a virtual Linux environment running on Android.\nTry commands like: ls, cd, cat, mkdir, echo, pwd, etc.\n\nType 'help' for a list of all supported commands.\n")
        createFile("home/user/Desktop/hello.sh",
            "#!/bin/bash\necho \"Hello from Linux Simulator!\"\necho \"Running on Android\"\n")
        createFile("var/log/syslog",
            "May 17 00:00:01 android-linux kernel: Linux version 6.1.0-linuxsim\n" +
            "May 17 00:00:01 android-linux kernel: Command line: root=/dev/sda1 ro quiet splash\n" +
            "May 17 00:00:02 android-linux systemd[1]: Started Linux Simulator.\n")
        createFile("proc/version",
            "Linux version 6.1.0-linuxsim (gcc 13.2.0) #1 SMP PREEMPT_DYNAMIC\n")
        createFile("proc/cpuinfo",
            "processor\t: 0\nmodel name\t: ARM Cortex-A55\ncpu MHz\t\t: 2000.000\ncache size\t: 512 KB\n")
        createFile("proc/meminfo",
            "MemTotal:\t 4096000 kB\nMemFree:\t 2048000 kB\nMemAvailable:\t 3000000 kB\nSwapTotal:\t 1048576 kB\nSwapFree:\t 1048576 kB\n")

        // Set current dir to home
        currentPath = mutableListOf("home", "user")
    }

    private fun createPath(path: String, isDir: Boolean) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var node = root
        for ((i, part) in parts.withIndex()) {
            val isLast = i == parts.size - 1
            node = node.children.getOrPut(part) {
                VFSNode(part, if (isLast) isDir else true)
            }
        }
    }

    private fun createFile(path: String, content: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var node = root
        for ((i, part) in parts.withIndex()) {
            val isLast = i == parts.size - 1
            if (isLast) {
                node.children[part] = VFSNode(part, false, content = content)
            } else {
                node = node.children.getOrPut(part) { VFSNode(part, true) }
            }
        }
    }

    fun currentDir(): VFSNode {
        var node = root
        for (p in currentPath) node = node.children[p] ?: return root
        return node
    }

    fun currentPathStr(): String {
        return if (currentPath.isEmpty()) "/" else "/" + currentPath.joinToString("/")
    }

    fun resolve(path: String): VFSNode? {
        val parts = resolveParts(path) ?: return null
        var node = root
        for (p in parts) {
            node = node.children[p] ?: return null
        }
        return node
    }

    fun resolveParts(path: String): MutableList<String>? {
        val parts: MutableList<String> = if (path.startsWith("/")) {
            mutableListOf()
        } else {
            currentPath.toMutableList()
        }
        for (seg in path.split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts
    }

    fun getOrCreateNode(path: String, isDirectory: Boolean): VFSNode? {
        val parts = resolveParts(path) ?: return null
        if (parts.isEmpty()) return root
        var node = root
        for ((i, p) in parts.withIndex()) {
            val isLast = i == parts.size - 1
            if (isLast) {
                if (!node.children.containsKey(p)) {
                    node.children[p] = VFSNode(p, isDirectory)
                }
                return node.children[p]
            } else {
                node = node.children.getOrPut(p) { VFSNode(p, true) }
            }
        }
        return null
    }

    fun parentOf(parts: List<String>): VFSNode? {
        if (parts.isEmpty()) return null
        var node = root
        for (i in 0 until parts.size - 1) {
            node = node.children[parts[i]] ?: return null
        }
        return node
    }

    fun deleteNode(path: String): Boolean {
        val parts = resolveParts(path) ?: return false
        if (parts.isEmpty()) return false
        val parent = parentOf(parts) ?: return false
        val name = parts.last()
        return parent.children.remove(name) != null
    }
}
