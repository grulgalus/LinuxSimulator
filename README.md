# Linux Simulator for Android

A fully-featured Linux terminal simulator for Android. Runs a virtual Linux filesystem in memory with 60+ commands, ANSI colors, tab completion, and command history.

![Platform](https://img.shields.io/badge/Platform-Android%206%2B-green)
![Build](https://github.com/YOUR_USERNAME/LinuxSimulator/actions/workflows/build.yml/badge.svg)

## Features

- **60+ Linux commands** — ls, cd, cat, grep, find, sed, awk, ps, top, df, free, apt, systemctl, and more
- **Virtual filesystem** — Full in-memory Linux directory tree (`/home`, `/etc`, `/var`, `/proc`, etc.)
- **ANSI color output** — Dracula-themed color scheme
- **Tab completion** — Autocomplete filenames
- **Command history** — Navigate with ▲/▼ buttons
- **Pipes** — `ls | grep txt`, `cat file | wc -l`
- **Package managers** — Simulated `apt` and `pacman`
- **Process management** — `ps`, `top`, `systemctl`
- **File editing** — `echo "text" > file`, `cat >> file`

## Supported Commands

| Category | Commands |
|----------|----------|
| Filesystem | `ls`, `cd`, `pwd`, `mkdir`, `rm`, `cp`, `mv`, `touch`, `find`, `stat`, `file` |
| Text | `cat`, `echo`, `grep`, `wc`, `head`, `tail`, `sort`, `sed`, `diff`, `cut`, `tr` |
| System | `uname`, `whoami`, `id`, `hostname`, `date`, `uptime`, `ps`, `top`, `free`, `df`, `du` |
| Network | `ping`, `ifconfig`, `ip`, `netstat`, `curl` (simulated) |
| Packages | `apt install/remove/update`, `pacman -S/-R/-Syu` |
| Services | `systemctl start/stop/status/enable`, `journalctl` |
| Shell | `alias`, `export`, `history`, `which`, `expr`, `seq`, `factor` |
| Info | `lscpu`, `lsblk`, `lsusb`, `lspci`, `dmesg` |

## Building

### GitHub Actions (automatic)
Push to `main` — GitHub Actions builds debug and release APKs automatically.
Download from the **Actions** tab → latest workflow run → **Artifacts**.

### Local build
```bash
# Requires JDK 17 + Android SDK
git clone https://github.com/YOUR_USERNAME/LinuxSimulator.git
cd LinuxSimulator
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Termux build
```bash
pkg install git openjdk-17
git clone https://github.com/YOUR_USERNAME/LinuxSimulator.git
cd LinuxSimulator
# Set ANDROID_HOME to your SDK path
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

## Quick Start

1. Open the app — you start in `/home/user`
2. Type `help` to see all commands
3. Try `ls`, `cd Documents`, `cat readme.txt`
4. Use `Tab` for autocomplete, `▲▼` for history
5. Use `^C` to cancel input

## Project Structure

```
LinuxSimulator/
├── .github/workflows/build.yml     # GitHub Actions CI
├── app/src/main/
│   ├── java/com/linuxsim/
│   │   ├── MainActivity.kt         # Terminal UI
│   │   ├── ShellInterpreter.kt     # Command parser & executor
│   │   └── VirtualFileSystem.kt    # In-memory filesystem
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/{themes,colors,strings}.xml
└── gradle/
    └── libs.versions.toml
```

## Requirements

- Android 8.0+ (API 26)
- ~5MB storage

## License

MIT
