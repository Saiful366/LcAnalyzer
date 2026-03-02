# LcAnalyzer

**Fiji / ImageJ Plugin for Chemi & Membrane Image Analysis**

> Author: Saiful Islam | Version: 1.0.6 | Platform: Fiji / ImageJ (macOS)

---

## Overview

LcAnalyzer is a Fiji/ImageJ plugin for analysing Chemi and Membrane images. It provides a floating control panel with keyboard shortcuts (F1/F2) for ROI thresholding, particle analysis, CSV export, drawing export, and image export — with left-to-right ordered results and silent window management.

---

## Requirements

- macOS (tested on macOS with Fiji)
- [Fiji](https://fiji.sc/) — download and install
- Java 8 or higher (bundled with Fiji)
- **Apache Maven 3.x** — required for building from source (see Installation)

---

## Installation

### 1. Install Maven

**Option A — Homebrew (recommended):**
```bash
# Install Homebrew if not already installed:
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Add Homebrew to PATH (copy the 3 lines shown at end of install):
echo >> ~/.zprofile
echo 'eval "$(/opt/homebrew/bin/brew shellenv zsh)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv zsh)"

# Install Maven:
brew install maven
```

**Option B — Conda:**
```bash
conda install -c conda-forge maven
```

### 2. Clone or Download

```bash
# Clone:
git clone https://github.com/Saiful366/LcAnalyzer.git
cd LcAnalyzer

# Or download the zip and unzip:
cd ~/Downloads/Lc_Analyzer
```

### 3. Build

```bash
mvn clean package
```

A successful build ends with `BUILD SUCCESS` and produces `target/Lc-Analyzer.jar`.

### 4. Install into Fiji

```bash
cp target/Lc-Analyzer.jar /Applications/Fiji.app/plugins/
```

Restart Fiji. The plugin appears under **Plugins > LcAnalyzer**.

---

## How to Use

### Launch
Go to **Plugins > LcAnalyzer**. A floating control panel opens.

### Workflow

1. Click **Browse Image...** to open your image. Mode (Chemi/Membrane) is auto-detected from the filename.
2. Draw a **rectangle ROI** on the image.
3. Press **F1** (or click *ROI to Threshold [F1]*) — duplicates the ROI as SelectedROI, inverts if Chemi, opens Colour Threshold.
4. Adjust the threshold to select your signal.
5. Click **Select** in the Colour Threshold panel. *(Required before F2 will proceed.)*
6. Press **F2** (or click *Apply and Analyze [F2]*) — runs analysis:
   - **Chemi mode:** runs Measure, records area, mean, intensity etc.
   - **Membrane mode:** runs Analyse Particles (≥ 80 px), sorts ROIs left-to-right, labels Drawing and Results table in matching order.
7. Results **accumulate** across multiple analyses. Use **Close All** to reset.

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `F1` (fn+F1 on Mac) | Duplicate ROI → SelectedROI, invert (Chemi), open Colour Threshold |
| `F2` (fn+F2 on Mac) | Run Measure (Chemi) or Analyse Particles (Membrane). Blocked until Select is clicked. |

Shortcuts work globally — even when the image window has focus.

---

## Control Panel Buttons

| Button | Colour | Description |
|--------|--------|-------------|
| Browse Image... | 🔵 Blue | Opens file browser; remembers last directory |
| ROI to Threshold [F1] | 🟢 Green | Duplicates ROI, opens Colour Threshold |
| Apply and Analyze [F2] | 🟠 Orange | Runs analysis. Blocked until Select clicked |
| Export Results as CSV | 🟣 Purple | Saves full Results table to .csv |
| Export Drawing of ROI | 🩵 Teal | Saves Drawing image as .png (Membrane only) |
| Export SelectedROI | 🟡 Amber | Saves SelectedROI image as .png |
| Close Results | ⚫ Grey | Silently closes Results window |
| Close SelectedROI | ⚫ Grey | Silently closes SelectedROI image |
| Close Drawing of ROI | ⚫ Grey | Silently closes Drawing window |
| Close All | 🔴 Red | Closes all images, resets all state |

---

## Mode Detection

Mode is auto-detected from the image filename:

- Filename contains `membrane` → **Membrane mode**
- Filename contains `chemi` → **Chemi mode**
- Neither found → defaults to **Chemi mode**

---

## Results Ordering (Membrane Mode)

After Analyse Particles, ROIs are sorted **left-to-right** by bounding box X position. Drawing labels (1, 2, 3…) and Results table row numbers always match. Labels continue sequentially across multiple analyses (e.g. analysis 1 gives 1–3, analysis 2 gives 4–6). **Close All** resets the counter.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `zsh: command not found: mvn` | Maven not installed. See Installation → Install Maven |
| `zsh: command not found: brew` | Run the 3 `eval` lines shown at end of Homebrew install |
| `BUILD FAILURE — no POM` | Wrong folder. Run: `cd ~/Downloads/Lc_Analyzer` |
| Plugin not in Fiji menu | Ensure jar is in `Fiji.app/plugins/` and Fiji restarted |
| F1/F2 do nothing | LcAnalyzer panel must be open first |
| F2 blocked | Click **Select** in Colour Threshold panel before F2 |
| Export Drawing greyed out | Only activates after a Membrane F2 analysis |
| Export SelectedROI greyed out | Activates after F2 for both Chemi and Membrane |

---

*LcAnalyzer v1.0.6 · Saiful Islam · Built with Fiji / ImageJ*
